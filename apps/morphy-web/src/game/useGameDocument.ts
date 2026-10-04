import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { ChessGame, GameInfoServices, GameTree } from 'game-view';
import { ApiError, createGame, fetchDatabases, fetchGame, replaceGame } from '../api/client';
import type { DatabaseResponse, GameDto } from '../api/types';
import { gameDtoToChessGame, gameToGamePatch } from './gameDtoAdapter';
import { databaseFormat, gameInfoServices, saveFideIds } from './gameServices';

export type GameState =
  | { kind: 'empty' }
  | { kind: 'loading' }
  | { kind: 'loaded'; game: GameDto; shown: string }
  | { kind: 'error'; message: string };

type FetchResult = { paramsKey: string; game: GameDto } | { paramsKey: string; error: string };

const BLANK_GAME: GameDto = {
  id: null,
  type: 'game',
  result: 'NOT_FINISHED',
  date: { year: 0, month: 0, day: 0 },
};

const EMPTY_GAME: ChessGame = { header: { id: 'new', white: '', black: '', result: '', date: '' }, tags: [] };

/** "White vs Black" by the players' last names, or null if the game has neither. */
export function gameTitle(game: GameDto): string | null {
  const white = game.whitePlayer?.lastName?.trim();
  const black = game.blackPlayer?.lastName?.trim();
  if (!white && !black) return null;
  return `${white || '?'} vs ${black || '?'}`;
}

export interface GameDocument {
  /** The configured databases, once loaded */
  databases: DatabaseResponse[] | null;
  gameState: GameState;
  /** The game for GameView; a new object only when a different game is loaded */
  selectedGame: ChessGame;
  /** For GameView's Edit Game Info dialog, when the game belongs to a database */
  gameInfoServices: GameInfoServices | undefined;
  /** Pass to GameView's onGameReady, so that save() can read the edited game */
  onGameReady: (game: GameTree) => void;
  canSave: boolean;
  saving: boolean;
  /** Saves the edited game: replaces it, or creates it if it's new */
  save: () => Promise<void>;
  /** A message about the last save, or one set with showMessage */
  message: string | null;
  showMessage: (message: string) => void;
}

/**
 * A game shown in GameView: loaded from a database by id, or new (empty), and saved back to the
 * database. A new game that is saved is created; onCreated then gets its id, for the caller to
 * pass back as gameId.
 */
export function useGameDocument(
  databaseId: string | null,
  gameId: number | null,
  onCreated?: (databaseId: string, gameId: number) => void
): GameDocument {
  const paramsKey = `${databaseId ?? ''}:${gameId ?? ''}`;

  const [databases, setDatabases] = useState<DatabaseResponse[] | null>(null);
  const [fetchResult, setFetchResult] = useState<FetchResult | null>(null);
  // Namespaced by paramsKey rather than reset via an effect/ref: a message from a previous
  // db/game just stops matching and disappears on its own once the user navigates away.
  const [messageFor, setMessageFor] = useState<{ paramsKey: string; message: string } | null>(null);
  const message = messageFor?.paramsKey === paramsKey ? messageFor.message : null;
  const [saving, setSaving] = useState(false);
  const gameRef = useRef<GameTree | null>(null);
  // The format decides the languages a game tag can have titles in
  const format = databaseFormat(databases?.find((db) => db.id === databaseId)?.path);
  const services = useMemo(
    () => (databaseId ? gameInfoServices(databaseId, format) : undefined),
    [databaseId, format]
  );

  // Load the database list once, both for the format and to catch an unknown db.
  useEffect(() => {
    fetchDatabases()
      .then((res) => setDatabases(res.databases))
      .catch((err) => console.error('Failed to load databases:', err));
  }, []);

  // The purely synchronous cases don't need a fetch at all, so they're computed straight from
  // props/state rather than mirrored into state via an effect.
  const syncState: GameState | null = useMemo(() => {
    if (!databaseId) return { kind: 'empty' };
    if (databases && !databases.some((db) => db.id === databaseId)) {
      return { kind: 'error', message: `Unknown database '${databaseId}'` };
    }
    if (!gameId) return { kind: 'empty' };
    return null; // db + game both set: needs a fetch, see below.
  }, [databaseId, gameId, databases]);

  // Only db + game actually needs to talk to the server. setState only ever happens inside the
  // async callbacks here, never synchronously in the effect body.
  useEffect(() => {
    if (syncState !== null || !databaseId || !gameId) return;
    let cancelled = false;
    fetchGame(databaseId, gameId)
      .then((game) => {
        if (!cancelled) setFetchResult({ paramsKey, game });
      })
      .catch((err: unknown) => {
        if (cancelled) return;
        const message =
          err instanceof ApiError && err.status === 404
            ? `Game ${gameId} not found in database '${databaseId}'`
            : err instanceof Error
              ? err.message
              : String(err);
        setFetchResult({ paramsKey, error: message });
      });
    return () => {
      cancelled = true;
    };
  }, [databaseId, gameId, syncState, paramsKey]);

  const gameState: GameState = useMemo(() => {
    if (syncState) return syncState;
    if (!fetchResult || fetchResult.paramsKey !== paramsKey) return { kind: 'loading' };
    return 'error' in fetchResult
      ? { kind: 'error', message: fetchResult.error }
      : { kind: 'loaded', game: fetchResult.game, shown: JSON.stringify(gameDtoToChessGame(fetchResult.game)) };
  }, [syncState, fetchResult, paramsKey]);

  // Keyed on primitive values (not the gameState object) so that saving a replaced game
  // - which produces a new GameState object with the *same* game - doesn't change identity
  // and doesn't make GameView reload/reset the board out from under the user.
  const shownGame = gameState.kind === 'loaded' ? gameState.shown : '';
  const selectedGame = useMemo<ChessGame>(
    () => (shownGame ? (JSON.parse(shownGame) as ChessGame) : EMPTY_GAME),
    [shownGame]
  );

  const onGameReady = useCallback((game: GameTree) => {
    gameRef.current = game;
  }, []);

  const showMessage = useCallback((message: string) => setMessageFor({ paramsKey, message }), [paramsKey]);

  const canSave = !saving && Boolean(databaseId) && (gameState.kind === 'loaded' || gameState.kind === 'empty');

  async function save() {
    if (!databaseId || !gameRef.current) return;
    setSaving(true);
    try {
      if (gameState.kind === 'loaded' && gameId) {
        const patch = gameToGamePatch(gameRef.current, gameState.game);
        let updated = await replaceGame(databaseId, gameId, patch);
        if (await saveFideIds(databaseId, patch, updated)) updated = await fetchGame(databaseId, gameId);
        // Keep the same paramsKey/game (see the comment on shownGame above) - only the
        // metadata changes.
        setFetchResult({ paramsKey, game: updated });
        setMessageFor({ paramsKey, message: 'Saved.' });
      } else {
        const patch = gameToGamePatch(gameRef.current, BLANK_GAME);
        let created = await createGame(databaseId, patch);
        if (created.id == null) {
          throw new Error('Server did not return an id for the created game');
        }
        const createdId = created.id;
        if (await saveFideIds(databaseId, patch, created)) created = await fetchGame(databaseId, createdId);
        const newParamsKey = `${databaseId}:${createdId}`;
        onCreated?.(databaseId, createdId);
        setFetchResult({ paramsKey: newParamsKey, game: created });
        setMessageFor({ paramsKey: newParamsKey, message: 'Created.' });
      }
    } catch (err) {
      setMessageFor({ paramsKey, message: err instanceof Error ? err.message : String(err) });
    } finally {
      setSaving(false);
    }
  }

  return {
    databases,
    gameState,
    selectedGame,
    gameInfoServices: services,
    onGameReady,
    canSave,
    saving,
    save,
    message,
    showMessage,
  };
}

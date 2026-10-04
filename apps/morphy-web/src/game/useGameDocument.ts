import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { forgetEntityIds, GameTree } from 'game-view';
import type { ChessGame, GameInfoServices } from 'game-view';
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

export interface GameDocumentOptions {
  /** Called when a new game is saved, with its database and id, for the caller to pass back */
  onCreated?: (databaseId: string, gameId: number) => void;
  /**
   * Called when loading or saving the game fails, with what failed; without it, a failed save is
   * shown as the message, and a failed load as the error state.
   */
  onError?: (what: string, err: unknown) => void;
  /** Called when the game is saved, with a word on it; without it, that's shown as the message */
  onSaved?: (message: string) => void;
}

const BLANK_GAME: GameDto = {
  id: null,
  type: 'game',
  result: 'NOT_FINISHED',
  date: { year: 0, month: 0, day: 0 },
};

const EMPTY_GAME: ChessGame = { header: { id: 'new', white: '', black: '', result: '', date: '' }, tags: [] };

/** A player's last name, or null if it's unknown: none, or "?" as in PGN. */
function playerName(player: GameDto['whitePlayer']): string | null {
  const name = player?.lastName?.trim();
  return name && name !== '?' ? name : null;
}

/** "White vs Black" by the players' last names, or null if neither is known. */
export function gameTitle(game: GameDto): string | null {
  const white = playerName(game.whitePlayer);
  const black = playerName(game.blackPlayer);
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
  /**
   * Saves the edited game: replaces it, or creates it if it's new. A game in no database is
   * created in the one given.
   */
  save: (toDatabaseId?: string) => Promise<void>;
  /**
   * Saves what's on the board as a new game in a database, maybe the game's own, which the
   * board then shows.
   */
  saveAs: (toDatabaseId: string) => Promise<void>;
  /** A message about the last save, or one set with showMessage */
  message: string | null;
  showMessage: (message: string) => void;
}

/**
 * A game shown in GameView: loaded from a database by id, or new (empty), and saved back to the
 * database. A new game that is saved is created, in its database or, if it has none, the one
 * save is given; onCreated then gets the database and the id, for the caller to pass back.
 */
export function useGameDocument(
  databaseId: string | null,
  gameId: number | null,
  { onCreated, onError, onSaved }: GameDocumentOptions = {}
): GameDocument {
  const paramsKey = `${databaseId ?? ''}:${gameId ?? ''}`;

  const [databases, setDatabases] = useState<DatabaseResponse[] | null>(null);
  const [fetchResult, setFetchResult] = useState<FetchResult | null>(null);
  // Namespaced by paramsKey rather than reset via an effect/ref: a message from a previous
  // db/game just stops matching and disappears on its own once the user navigates away.
  const [messageFor, setMessageFor] = useState<{ paramsKey: string; message: string } | null>(null);
  const message = messageFor?.paramsKey === paramsKey ? messageFor.message : null;
  const [saving, setSaving] = useState(false);
  // A new game just created from what's on the board: the board already shows it, so it keeps
  // what it shows rather than load the game again, which would go back to its start
  const [createdShown, setCreatedShown] = useState<{ paramsKey: string; shown: string } | null>(null);
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
        onError?.('Opening the game failed', err);
      });
    return () => {
      cancelled = true;
    };
    // onError isn't a dependency: a new one mustn't load the game again
    // eslint-disable-next-line react-hooks/exhaustive-deps
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
  const shownGame =
    createdShown?.paramsKey === paramsKey ? createdShown.shown : gameState.kind === 'loaded' ? gameState.shown : '';
  const selectedGame = useMemo<ChessGame>(
    () => (shownGame ? (JSON.parse(shownGame) as ChessGame) : EMPTY_GAME),
    [shownGame]
  );

  const onGameReady = useCallback((game: GameTree) => {
    gameRef.current = game;
  }, []);

  const showMessage = useCallback((message: string) => setMessageFor({ paramsKey, message }), [paramsKey]);

  const canSave = !saving && (gameState.kind === 'loaded' || gameState.kind === 'empty');

  // Creates a game from what's on the board in a database; the board then shows that game, as
  // it is (see createdShown)
  async function createIn(targetId: string, patch: GameDto, message: string) {
    let created = await createGame(targetId, patch);
    if (created.id == null) {
      throw new Error('Server did not return an id for the created game');
    }
    const createdId = created.id;
    if (await saveFideIds(targetId, patch, created)) created = await fetchGame(targetId, createdId);
    const newParamsKey = `${targetId}:${createdId}`;
    onCreated?.(targetId, createdId);
    setCreatedShown({ paramsKey: newParamsKey, shown: shownGame });
    setFetchResult({ paramsKey: newParamsKey, game: created });
    if (onSaved) onSaved(message);
    else setMessageFor({ paramsKey: newParamsKey, message });
  }

  // Runs a save, telling of its failure as what failed
  async function runSave(what: string, operation: () => Promise<void>) {
    setSaving(true);
    try {
      await operation();
    } catch (err) {
      if (onError) onError(what, err);
      else setMessageFor({ paramsKey, message: err instanceof Error ? err.message : String(err) });
    } finally {
      setSaving(false);
    }
  }

  async function save(toDatabaseId?: string) {
    const targetId = databaseId ?? toDatabaseId;
    const tree = gameRef.current;
    if (!targetId || !tree) return;
    await runSave('Saving the game failed', async () => {
      if (databaseId && gameState.kind === 'loaded' && gameId) {
        const patch = gameToGamePatch(tree, gameState.game);
        let updated = await replaceGame(databaseId, gameId, patch);
        if (await saveFideIds(databaseId, patch, updated)) updated = await fetchGame(databaseId, gameId);
        // Keep the same paramsKey/game (see the comment on shownGame above) - only the
        // metadata changes.
        setFetchResult({ paramsKey, game: updated });
        if (onSaved) onSaved('Saved.');
        else setMessageFor({ paramsKey, message: 'Saved.' });
      } else {
        await createIn(targetId, gameToGamePatch(tree, BLANK_GAME), 'Created.');
      }
    });
  }

  async function saveAs(toDatabaseId: string) {
    const tree = gameRef.current;
    if (!tree) return;
    await runSave('Saving a copy of the game failed', async () => {
      // In another database, the players, tournament and the rest are found or created by name:
      // the ids are this database's. They're forgotten on a copy, so the board keeps them if
      // saving fails, and on the board once the game is saved there.
      const otherDatabase = toDatabaseId !== databaseId;
      const source = otherDatabase ? GameTree.fromMoves(tree.toMoves(), Object.entries(tree.tagValues())) : tree;
      if (otherDatabase) forgetEntityIds(source);
      await createIn(toDatabaseId, gameToGamePatch(source, BLANK_GAME), 'Saved as a new game.');
      if (otherDatabase) forgetEntityIds(tree);
    });
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
    saveAs,
    message,
    showMessage,
  };
}

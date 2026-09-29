import { useEffect, useMemo, useRef, useState } from 'react';
import type { Chess } from '@jackstenglein/chess';
import { GameView } from 'game-view';
import type { ChessGame, TournamentService } from 'game-view';
import {
  ApiError,
  createGame,
  fetchDatabases,
  fetchGame,
  fetchTournament,
  replaceGame,
  search,
  updateTournament,
} from '../api/client';
import { gameDtoToPgn, pgnToGamePatch, tournamentDto, tournamentInfo } from './gameDtoAdapter';
import type { DatabaseResponse, EntitySearchResponse, GameDto, TournamentDto } from '../api/types';
import { useDbGameParams } from './hooks/useDbGameParams';
import './App.css';

type GameState =
  | { kind: 'empty' }
  | { kind: 'loading' }
  | { kind: 'loaded'; game: GameDto; pgn: string }
  | { kind: 'error'; message: string };

type FetchResult = { paramsKey: string; game: GameDto } | { paramsKey: string; error: string };

const BLANK_GAME: GameDto = {
  id: null,
  type: 'game',
  result: 'NOT_FINISHED',
  date: { year: 0, month: 0, day: 0 },
};

/** Existing tournaments of a database, for the Edit Game Info dialog. */
function tournamentService(databaseId: string): TournamentService {
  const info = (dto: TournamentDto) => tournamentInfo(dto)!;
  return {
    async search(text) {
      // A quoted value matches titles that start with the whole text
      const filter = `"${text.replace(/"/g, '')}"`;
      const response = await search<EntitySearchResponse<TournamentDto>>(databaseId, 'tournaments', {
        filter,
        limit: 20,
        sortBy: '-startDate',
      });
      return response.items.map(info);
    },
    async get(id) {
      return info(await fetchTournament(databaseId, id));
    },
    async update(tournament) {
      // The whole entity is replaced, so start from it as saved to keep the fields not edited here
      const current = await fetchTournament(databaseId, tournament.id!);
      return info(await updateTournament(databaseId, { ...current, ...tournamentDto(tournament) }));
    },
  };
}

function App() {
  const { databaseId, gameId, setLoadedGame } = useDbGameParams();
  const paramsKey = `${databaseId ?? ''}:${gameId ?? ''}`;

  const [databases, setDatabases] = useState<DatabaseResponse[] | null>(null);
  const [fetchResult, setFetchResult] = useState<FetchResult | null>(null);
  // Namespaced by paramsKey rather than reset via an effect/ref: a message from a previous
  // db/game just stops matching and disappears on its own once the user navigates away.
  const [saveMessageFor, setSaveMessageFor] = useState<{ paramsKey: string; message: string } | null>(null);
  const saveMessage = saveMessageFor?.paramsKey === paramsKey ? saveMessageFor.message : null;
  const [saving, setSaving] = useState(false);
  const chessRef = useRef<Chess | null>(null);
  const tournaments = useMemo(() => (databaseId ? tournamentService(databaseId) : undefined), [databaseId]);

  // Load the database list once, both for the status bar and to catch an unknown db.
  useEffect(() => {
    fetchDatabases()
      .then((res) => setDatabases(res.databases))
      .catch((err) => console.error('Failed to load databases:', err));
  }, []);

  // The three purely synchronous scenarios (see the plan) don't need a fetch at all, so they're
  // computed straight from props/state rather than mirrored into state via an effect.
  const syncState: GameState | null = useMemo(() => {
    if (!databaseId) return { kind: 'empty' };
    if (databases && !databases.some((db) => db.id === databaseId)) {
      return { kind: 'error', message: `Unknown database '${databaseId}'` };
    }
    if (!gameId) return { kind: 'empty' };
    return null; // db + game both set: needs a fetch, see below.
  }, [databaseId, gameId, databases]);

  // Only the fourth scenario (db + game) actually needs to talk to the server. setState only
  // ever happens inside the async callbacks here, never synchronously in the effect body.
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
      : { kind: 'loaded', game: fetchResult.game, pgn: gameDtoToPgn(fetchResult.game) };
  }, [syncState, fetchResult, paramsKey]);

  // Keyed on primitive values (not the gameState object) so that saving a replaced game
  // - which produces a new GameState object with the *same* pgn - doesn't change identity
  // and doesn't make GameView reload/reset the board out from under the user.
  const loadedPgn = gameState.kind === 'loaded' ? gameState.pgn : '';
  const loadedId = gameState.kind === 'loaded' ? String(gameState.game.id ?? 'new') : 'new';
  const selectedGame = useMemo<ChessGame>(
    () => ({
      header: { id: loadedId, white: '', black: '', result: '', date: '' },
      pgn: loadedPgn,
    }),
    [loadedId, loadedPgn]
  );

  const canSave = !saving && Boolean(databaseId) && (gameState.kind === 'loaded' || gameState.kind === 'empty');

  async function handleSave() {
    if (!databaseId || !chessRef.current) return;
    setSaving(true);
    try {
      if (gameState.kind === 'loaded' && gameId) {
        const patch = pgnToGamePatch(chessRef.current, gameState.game);
        const updated = await replaceGame(databaseId, gameId, patch);
        // Keep the same paramsKey/pgn (see the comment on loadedPgn above) - only the
        // metadata changes.
        setFetchResult({ paramsKey, game: updated });
        setSaveMessageFor({ paramsKey, message: 'Saved.' });
      } else {
        const patch = pgnToGamePatch(chessRef.current, BLANK_GAME);
        const created = await createGame(databaseId, patch);
        if (created.id == null) {
          throw new Error('Server did not return an id for the created game');
        }
        const newParamsKey = `${databaseId}:${created.id}`;
        setLoadedGame(databaseId, created.id);
        setFetchResult({ paramsKey: newParamsKey, game: created });
        setSaveMessageFor({ paramsKey: newParamsKey, message: 'Created.' });
      }
    } catch (err) {
      setSaveMessageFor({ paramsKey, message: err instanceof Error ? err.message : String(err) });
    } finally {
      setSaving(false);
    }
  }

  const databaseName = databases?.find((db) => db.id === databaseId)?.displayName ?? databaseId;
  const statusText = !databaseId
    ? 'No database (unbound)'
    : gameState.kind === 'loaded'
      ? `${databaseName} — game ${gameState.game.id}`
      : `${databaseName} — new game`;

  return (
    <div className="app">
      <header className="topbar">
        <h1>Board Tester</h1>
        <span className="status">{statusText}</span>
        <button onClick={handleSave} disabled={!canSave} title={!databaseId ? 'No database to save to' : undefined}>
          {saving ? 'Saving…' : 'Save'}
        </button>
        {saveMessage && <span className="save-message">{saveMessage}</span>}
        {gameState.kind === 'error' && <span className="error-message">{gameState.message}</span>}
      </header>
      <main>
        <GameView
          selectedGame={selectedGame}
          initialOrientation="white"
          onChessReady={(chess) => {
            chessRef.current = chess;
          }}
          tournamentService={tournaments}
        />
      </main>
    </div>
  );
}

export default App;

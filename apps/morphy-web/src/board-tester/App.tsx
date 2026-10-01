import { useEffect, useMemo, useRef, useState } from 'react';
import type { Chess } from '@jackstenglein/chess';
import { GameView, gameTagLanguages } from 'game-view';
import type {
  ChessGame,
  GameInfoServices,
  GameTagInfo,
  GameTagLanguage,
  GameTagService,
  PlayerService,
  SourceInfo,
  SourceService,
  TeamInfo,
  TeamService,
  TournamentInfo,
  TournamentService,
} from 'game-view';
import {
  ApiError,
  createGame,
  fetchDatabases,
  fetchEntity,
  fetchGame,
  replaceGame,
  search,
  updateEntity,
} from '../api/client';
import type { EntityPath } from '../api/client';
import {
  gameDtoToPgn,
  gameTagDto,
  gameTagInfo,
  pgnToGamePatch,
  sourceDto,
  sourceInfo,
  teamDto,
  teamInfo,
  tournamentDto,
  tournamentInfo,
} from './gameDtoAdapter';
import type {
  AnnotatorDto,
  DatabaseResponse,
  EntitySearchResponse,
  GameDto,
  GameTagDto,
  PlayerDto,
  SourceDto,
  TeamDto,
  TournamentDto,
} from '../api/types';
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

/** A quoted filter value matches names and titles that start with the whole text. */
function prefixFilter(text: string): string {
  return `"${text.replace(/"/g, '')}"`;
}

/** Entities of a database whose names or titles start with the text, those with the most games first. */
async function searchEntities<T>(databaseId: string, path: EntityPath, text: string, sortBy = '-count'): Promise<T[]> {
  const response = await search<EntitySearchResponse<T>>(databaseId, path, {
    filter: prefixFilter(text),
    limit: 20,
    sortBy,
  });
  return response.items;
}

/** Existing players of a database, for the Edit Game Info dialog. */
function playerService(databaseId: string, fideIds: boolean): PlayerService {
  return {
    fideIds,
    async search(text) {
      return (await searchEntities<PlayerDto>(databaseId, 'players', text)).map((p) => ({
        id: p.id,
        name: p.firstName ? `${p.lastName ?? ''}, ${p.firstName}` : p.lastName ?? '',
        gameCount: p.gameCount,
        fideId: p.fideId,
      }));
    },
  };
}

/**
 * Gives the game's existing players the FIDE ids the game was saved with: a FIDE id belongs to the
 * player, so the game itself can't change it. Returns whether any player was changed.
 */
async function saveFideIds(databaseId: string, wanted: GameDto, saved: GameDto): Promise<boolean> {
  let changed = false;
  for (const [want, have] of [
    [wanted.whitePlayer, saved.whitePlayer],
    [wanted.blackPlayer, saved.blackPlayer],
  ]) {
    if (have?.id == null || (want?.fideId ?? null) === (have.fideId ?? null)) continue;
    // The whole entity is replaced, so start from it as saved; 0 is no FIDE id
    const current = await fetchEntity<PlayerDto>(databaseId, 'players', have.id);
    await updateEntity(databaseId, 'players', { ...current, fideId: want?.fideId ?? 0 });
    changed = true;
  }
  return changed;
}

/** Existing annotators of a database, for the Edit Game Info dialog. */
function annotatorService(databaseId: string): PlayerService {
  return {
    async search(text) {
      return (await searchEntities<AnnotatorDto>(databaseId, 'annotators', text)).map((a) => ({
        id: a.id,
        name: a.name ?? '',
        gameCount: a.gameCount,
      }));
    },
  };
}

/**
 * Existing entities of one kind in a database, for the Edit Game Info dialog: found, fetched and
 * changed as the dialog has them, converted to and from their DTOs.
 */
function entityService<D extends { id: number | null }, I extends { id: number | null }>(
  databaseId: string,
  path: EntityPath,
  toInfo: (dto: D) => I,
  toDto: (info: I) => Partial<D>,
  sortBy?: string
) {
  return {
    async search(text: string) {
      return (await searchEntities<D>(databaseId, path, text, sortBy)).map(toInfo);
    },
    async get(id: number) {
      return toInfo(await fetchEntity<D>(databaseId, path, id));
    },
    async update(info: I) {
      // The whole entity is replaced, so start from it as saved, keeping the fields not edited here
      const current = await fetchEntity<D>(databaseId, path, info.id!);
      return toInfo(await updateEntity<D>(databaseId, path, { ...current, ...toDto(info) }));
    },
  };
}

function sourceService(databaseId: string): SourceService {
  return entityService<SourceDto, SourceInfo>(databaseId, 'sources', (dto) => sourceInfo(dto)!, sourceDto);
}

function teamService(databaseId: string): TeamService {
  return entityService<TeamDto, TeamInfo>(databaseId, 'teams', (dto) => teamInfo(dto)!, teamDto);
}

function tournamentService(databaseId: string): TournamentService {
  // The latest first, as an older one of the same name is rarely meant
  return entityService<TournamentDto, TournamentInfo>(
    databaseId,
    'tournaments',
    (dto) => tournamentInfo(dto)!,
    tournamentDto,
    '-startDate'
  );
}

function gameTagService(databaseId: string, languages: GameTagLanguage[]): GameTagService {
  // The empty placeholder tag has no titles
  const info = (dto: GameTagDto): GameTagInfo => gameTagInfo(dto) ?? { id: dto.id, titles: {}, gameCount: dto.gameCount };
  return { languages, ...entityService<GameTagDto, GameTagInfo>(databaseId, 'gametags', info, gameTagDto) };
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
  // The format decides the languages a game tag can have titles in
  const databasePath = databases?.find((db) => db.id === databaseId)?.path;
  const databaseFormat = databasePath?.endsWith('.2cbh') ? '2cbh' : databasePath?.endsWith('.cbh') ? 'cbh' : undefined;
  const gameInfoServices = useMemo<GameInfoServices | undefined>(
    () =>
      databaseId
        ? {
            players: playerService(databaseId, databaseFormat === '2cbh'),
            annotators: annotatorService(databaseId),
            tournaments: tournamentService(databaseId),
            sources: sourceService(databaseId),
            teams: teamService(databaseId),
            gameTags: gameTagService(databaseId, gameTagLanguages(databaseFormat)),
          }
        : undefined,
    [databaseId, databaseFormat]
  );

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
        let updated = await replaceGame(databaseId, gameId, patch);
        if (await saveFideIds(databaseId, patch, updated)) updated = await fetchGame(databaseId, gameId);
        // Keep the same paramsKey/pgn (see the comment on loadedPgn above) - only the
        // metadata changes.
        setFetchResult({ paramsKey, game: updated });
        setSaveMessageFor({ paramsKey, message: 'Saved.' });
      } else {
        const patch = pgnToGamePatch(chessRef.current, BLANK_GAME);
        let created = await createGame(databaseId, patch);
        if (created.id == null) {
          throw new Error('Server did not return an id for the created game');
        }
        const createdId = created.id;
        if (await saveFideIds(databaseId, patch, created)) created = await fetchGame(databaseId, createdId);
        const newParamsKey = `${databaseId}:${createdId}`;
        setLoadedGame(databaseId, createdId);
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
          gameInfoServices={gameInfoServices}
        />
      </main>
    </div>
  );
}

export default App;

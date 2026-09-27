import { useCallback, useState } from 'react';

export interface DbGameParams {
  databaseId: string | null;
  gameId: number | null;
}

function readParams(): DbGameParams {
  const params = new URLSearchParams(window.location.search);
  const databaseId = params.get('db');
  const gameIdParam = params.get('game');
  const gameId = gameIdParam ? Number(gameIdParam) : null;

  if (!databaseId && gameId != null) {
    // Invalid: a game id without a database is meaningless. Strip both params.
    window.history.replaceState(null, '', window.location.pathname);
    return { databaseId: null, gameId: null };
  }

  return { databaseId, gameId: Number.isFinite(gameId) ? gameId : null };
}

/**
 * Reads the `db`/`game` query params driving board-tester's four scenarios (see the plan).
 * No router library - this is a single view, so a plain URLSearchParams read plus
 * history.replaceState is enough.
 */
export function useDbGameParams(): DbGameParams & {
  /** Called after a successful createGame, to move into "loaded" state without a reload. */
  setLoadedGame: (databaseId: string, gameId: number) => void;
} {
  const [params, setParams] = useState<DbGameParams>(readParams);

  const setLoadedGame = useCallback((databaseId: string, gameId: number) => {
    const search = new URLSearchParams({ db: databaseId, game: String(gameId) });
    window.history.replaceState(null, '', `${window.location.pathname}?${search}`);
    setParams({ databaseId, gameId });
  }, []);

  return { ...params, setLoadedGame };
}

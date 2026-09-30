import type {
  DatabaseListResponse,
  DebugSearchResponse,
  FilterOptionsResponse,
  GameDto,
  SourceDto,
  TournamentDto,
} from './types';

const API_BASE = '/api';

/** Thrown for a non-2xx response, carrying the HTTP status. */
export class ApiError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

async function getJson<T>(url: string, what: string): Promise<T> {
  const res = await fetch(url);
  if (!res.ok) {
    const text = await res.text();
    throw new ApiError(res.status, `${what} failed: ${res.status} - ${text}`);
  }
  return res.json();
}

async function sendJson<T>(
  method: 'POST' | 'PUT',
  url: string,
  body: unknown,
  what: string
): Promise<T> {
  const res = await fetch(url, {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  if (!res.ok) {
    const text = await res.text();
    throw new ApiError(res.status, `${what} failed: ${res.status} - ${text}`);
  }
  return res.json();
}

function databaseUrl(databaseId: string): string {
  return `${API_BASE}/databases/${encodeURIComponent(databaseId)}`;
}

function toParams(request: object): URLSearchParams {
  const params = new URLSearchParams();
  Object.entries(request).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      params.append(key, String(value));
    }
  });
  return params;
}

export async function fetchDatabases(): Promise<DatabaseListResponse> {
  return getJson(`${API_BASE}/databases`, 'Fetch databases');
}

/**
 * The filter fields and sort fields of a database, for games or one entity kind.
 *
 * @param path the API path segment: games, players, tournaments, annotators, sources, teams or
 *     gametags
 */
export async function fetchFilterOptions(
  databaseId: string,
  path: string
): Promise<FilterOptionsResponse> {
  return getJson(`${databaseUrl(databaseId)}/filters/${path}`, 'Fetch filter options');
}

/**
 * A normal search of games or one entity kind; `R` is the search response of that path.
 *
 * @param path the API path segment, as for {@link fetchFilterOptions}
 */
export async function search<R>(databaseId: string, path: string, request: object): Promise<R> {
  const params = toParams(request);
  return getJson(`${databaseUrl(databaseId)}/${path}/search?${params}`, 'Search');
}

/**
 * A debug search: the normal search result plus the query plans and the raw records behind every
 * returned item. Databases without diagnostics answer 501.
 */
export async function debugSearch<R>(
  databaseId: string,
  path: string,
  request: object,
  executeAllPlans: boolean
): Promise<DebugSearchResponse<R>> {
  const params = toParams({ ...request, executeAllPlans });
  return getJson(`${databaseUrl(databaseId)}/debug/${path}/search?${params}`, 'Debug search');
}

/** Fetches a single game (with moves, no text) - used by board-tester to load a game. */
export async function fetchGame(databaseId: string, gameId: number): Promise<GameDto> {
  return getJson(
    `${databaseUrl(databaseId)}/games/${gameId}?includeMoves=true&includeText=false`,
    'Fetch game'
  );
}

/**
 * Adds a new game to the database. Entities the game refers to by id must already exist;
 * entities referred to only by name are found, or created if they don't exist.
 */
export async function createGame(databaseId: string, game: GameDto): Promise<GameDto> {
  return sendJson('POST', `${databaseUrl(databaseId)}/games`, game, 'Create game');
}

/** Replaces an existing game. Entity references resolve as in {@link createGame}. */
export async function replaceGame(
  databaseId: string,
  gameId: number,
  game: GameDto
): Promise<GameDto> {
  return sendJson('PUT', `${databaseUrl(databaseId)}/games/${gameId}`, game, 'Replace game');
}

/** Fetches one tournament. */
export async function fetchTournament(databaseId: string, id: number): Promise<TournamentDto> {
  return getJson(`${databaseUrl(databaseId)}/tournaments/${id}`, 'Fetch tournament');
}

/**
 * Replaces a tournament's fields, for every game in it. The whole entity is replaced: a field left
 * out is cleared.
 */
export async function updateTournament(databaseId: string, tournament: TournamentDto): Promise<TournamentDto> {
  return sendJson('PUT', `${databaseUrl(databaseId)}/tournaments/${tournament.id}`, tournament, 'Update tournament');
}

/** Fetches one source. */
export async function fetchSource(databaseId: string, id: number): Promise<SourceDto> {
  return getJson(`${databaseUrl(databaseId)}/sources/${id}`, 'Fetch source');
}

/** Replaces a source's fields, for every game from it. As for tournaments, a field left out is cleared. */
export async function updateSource(databaseId: string, source: SourceDto): Promise<SourceDto> {
  return sendJson('PUT', `${databaseUrl(databaseId)}/sources/${source.id}`, source, 'Update source');
}

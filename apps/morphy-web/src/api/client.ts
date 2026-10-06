import type {
  DatabaseListResponse,
  DatabaseResponse,
  DebugSearchResponse,
  FilterOptionsResponse,
  GameDto,
  GameSearchResponse,
  PositionSearchRequest,
  PositionSearchResponse,
} from './types';
import { mockSearchPosition } from './mockPositionSearch';
import { callFinished, callStarted, newRequestId, SESSION_ID } from '../logs/logStore';

const API_BASE = '/api';

/** Thrown for a failed call: a non-2xx answer (with its status), or none at all (status 0). */
export class ApiError extends Error {
  readonly status: number;
  /** What the call was for, e.g. 'Create game' */
  readonly what: string;
  /** The service's own message about the failure, if it gave one */
  readonly serverMessage: string | null;
  /** The id the call was sent with, to find it and the service's events about it in the logs */
  readonly requestId: string;

  constructor(status: number, what: string, serverMessage: string | null, requestId: string) {
    super(`${what} failed: ${serverMessage ?? (status ? `HTTP ${status}` : 'no answer from the service')}`);
    this.status = status;
    this.what = what;
    this.serverMessage = serverMessage;
    this.requestId = requestId;
  }
}

/** The service's message in an error answer: the error field of its JSON, or the text itself. */
function serverMessageOf(text: string): string | null {
  try {
    const body = JSON.parse(text) as { error?: unknown; message?: unknown };
    if (typeof body.error === 'string' && body.error) return body.error;
    if (typeof body.message === 'string' && body.message) return body.message;
  } catch {
    // not JSON
  }
  return text.trim() || null;
}

/**
 * Makes an API call, with the ids that tie it to the service's log events, and records it in the
 * logs, whether it succeeds or not.
 */
async function call<T>(method: 'GET' | 'POST' | 'PUT', url: string, what: string, body?: unknown): Promise<T> {
  const requestId = newRequestId();
  const started = performance.now();
  callStarted({ requestId, time: new Date().toISOString(), method, url, what });
  let res: Response;
  try {
    res = await fetch(url, {
      method,
      headers: {
        'X-Request-Id': requestId,
        'X-Session-Id': SESSION_ID,
        ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
      },
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err);
    callFinished(requestId, 0, performance.now() - started, message);
    throw new ApiError(0, what, null, requestId);
  }
  const duration = performance.now() - started;
  if (!res.ok) {
    const text = await res.text();
    callFinished(requestId, res.status, duration, text);
    throw new ApiError(res.status, what, serverMessageOf(text), requestId);
  }
  callFinished(requestId, res.status, duration);
  return res.json();
}

function getJson<T>(url: string, what: string): Promise<T> {
  return call('GET', url, what);
}

function sendJson<T>(method: 'POST' | 'PUT', url: string, body: unknown, what: string): Promise<T> {
  return call(method, url, what, body);
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
 * The reference databases, which have the indexes to search their games by position. The service
 * doesn't mark them yet: until it does, those with a year in their name are, named by their first
 * word and the year: Megabase '26 (or every database, if none has a year).
 */
export async function fetchReferenceDatabases(): Promise<(DatabaseResponse & { referenceName: string })[]> {
  const { databases } = await fetchDatabases();
  const marked = databases.filter((db): db is DatabaseResponse & { referenceName: string } => !!db.referenceName);
  if (marked.length > 0) return marked;
  const firstWord = (db: DatabaseResponse) => db.displayName.split(/\s+/)[0];
  const withYear = databases.flatMap((db) => {
    const year = db.displayName.match(/\b(19|20)(\d\d)\b/);
    return year ? [{ ...db, referenceName: `${firstWord(db)} '${year[2]}` }] : [];
  });
  return withYear.length > 0 ? withYear : databases.map((db) => ({ ...db, referenceName: firstWord(db) }));
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

/** The kinds of entity a game refers to, by the API path segment of each. */
export type EntityPath = 'players' | 'annotators' | 'tournaments' | 'sources' | 'teams' | 'gametags';

/** Fetches one entity. */
export async function fetchEntity<T>(databaseId: string, path: EntityPath, id: number): Promise<T> {
  return getJson(`${databaseUrl(databaseId)}/${path}/${id}`, `Fetch ${path}`);
}

/**
 * Replaces an entity's fields, for every game that refers to it. The whole entity is replaced: a
 * field left out is cleared, except a player's FIDE id, which is kept; 0 clears it.
 */
export async function updateEntity<T extends { id: number | null }>(
  databaseId: string,
  path: EntityPath,
  entity: T
): Promise<T> {
  return sendJson('PUT', `${databaseUrl(databaseId)}/${path}/${entity.id}`, entity, `Update ${path}`);
}

/**
 * The games of a reference database that reached a position, a page of them, with what was played
 * from it on the first page.
 *
 * Not in the service yet: answered by a mock until it is. It will be
 * `GET /databases/{id}/positions/search?fen=...&sortBy=...&offset=...&limit=...`, with filters on
 * the games to come.
 */
export async function searchPosition(databaseId: string, request: PositionSearchRequest): Promise<PositionSearchResponse> {
  return mockSearchPosition(databaseId, request, (id, page) => search<GameSearchResponse>(id, 'games', page));
}

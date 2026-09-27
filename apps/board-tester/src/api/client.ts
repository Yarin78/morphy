import type { DatabaseListResponse, GameDto } from './types';

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

export async function fetchDatabases(): Promise<DatabaseListResponse> {
  return getJson(`${API_BASE}/databases`, 'Fetch databases');
}

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

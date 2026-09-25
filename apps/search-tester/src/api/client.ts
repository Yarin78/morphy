import type {
  DatabaseListResponse,
  DebugSearchResponse,
  FilterOptionsResponse,
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

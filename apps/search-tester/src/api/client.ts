import type {
  AnnotatorDto,
  AnnotatorListResponse,
  DatabaseListResponse,
  EntitySearchRequest,
  EntitySearchResponse,
  FilterOptionsResponse,
  GameSearchRequest,
  GameSearchResponse,
  GameTagDto,
  GameTagListResponse,
  PlayerDto,
  PlayerListResponse,
  SourceDto,
  SourceListResponse,
  TeamDto,
  TeamListResponse,
  TournamentDto,
  TournamentListResponse,
} from './types';

const API_BASE = '/api';

export async function fetchDatabases(): Promise<DatabaseListResponse> {
  const res = await fetch(`${API_BASE}/databases`);
  if (!res.ok) {
    throw new Error(`Failed to fetch databases: ${res.status} ${res.statusText}`);
  }
  return res.json();
}

const FILTER_API_PATH: Record<string, string> = {
  Games: 'games',
  Players: 'players',
  Tournaments: 'tournaments',
  Annotators: 'annotators',
  Sources: 'sources',
  Teams: 'teams',
  GameTags: 'gametags',
};

export async function fetchFilterOptions(entityType: string): Promise<FilterOptionsResponse> {
  const path = FILTER_API_PATH[entityType];
  if (!path) {
    return {
      defaultField: 'id',
      fields: ['id'],
      sortFields: [{ name: 'id', defaultDirection: 'asc' }],
    };
  }
  const res = await fetch(`${API_BASE}/filters/${path}`);
  if (!res.ok) {
    throw new Error(`Failed to fetch filter options: ${res.status} ${res.statusText}`);
  }
  return res.json();
}

export async function searchGames(
  databaseId: string,
  request: GameSearchRequest
): Promise<GameSearchResponse> {
  const params = new URLSearchParams();
  Object.entries(request).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      params.append(key, String(value));
    }
  });
  const url = `${API_BASE}/databases/${encodeURIComponent(databaseId)}/games/search?${params}`;
  const res = await fetch(url);
  if (!res.ok) {
    const text = await res.text();
    throw new Error(`Search failed: ${res.status} - ${text}`);
  }
  return res.json();
}

export async function searchGamesPost(
  databaseId: string,
  request: GameSearchRequest
): Promise<GameSearchResponse> {
  const res = await fetch(
    `${API_BASE}/databases/${encodeURIComponent(databaseId)}/games/search`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request),
    }
  );
  if (!res.ok) {
    const text = await res.text();
    throw new Error(`Search failed: ${res.status} - ${text}`);
  }
  return res.json();
}

export async function fetchPlayers(
  databaseId: string,
  cursor?: number,
  limit = 100
): Promise<PlayerListResponse> {
  const params = new URLSearchParams();
  if (cursor != null) params.append('cursor', String(cursor));
  params.append('limit', String(limit));
  const url = `${API_BASE}/databases/${encodeURIComponent(databaseId)}/players?${params}`;
  const res = await fetch(url);
  if (!res.ok) {
    const text = await res.text();
    throw new Error(`Failed to fetch players: ${res.status} - ${text}`);
  }
  return res.json();
}

export async function fetchTournaments(
  databaseId: string,
  cursor?: number,
  limit = 100
): Promise<TournamentListResponse> {
  const params = new URLSearchParams();
  if (cursor != null) params.append('cursor', String(cursor));
  params.append('limit', String(limit));
  const url = `${API_BASE}/databases/${encodeURIComponent(databaseId)}/tournaments?${params}`;
  const res = await fetch(url);
  if (!res.ok) {
    const text = await res.text();
    throw new Error(`Failed to fetch tournaments: ${res.status} - ${text}`);
  }
  return res.json();
}

export async function fetchAnnotators(
  databaseId: string,
  cursor?: number,
  limit = 100
): Promise<AnnotatorListResponse> {
  const params = new URLSearchParams();
  if (cursor != null) params.append('cursor', String(cursor));
  params.append('limit', String(limit));
  const url = `${API_BASE}/databases/${encodeURIComponent(databaseId)}/annotators?${params}`;
  const res = await fetch(url);
  if (!res.ok) {
    const text = await res.text();
    throw new Error(`Failed to fetch annotators: ${res.status} - ${text}`);
  }
  return res.json();
}

export async function fetchSources(
  databaseId: string,
  cursor?: number,
  limit = 100
): Promise<SourceListResponse> {
  const params = new URLSearchParams();
  if (cursor != null) params.append('cursor', String(cursor));
  params.append('limit', String(limit));
  const url = `${API_BASE}/databases/${encodeURIComponent(databaseId)}/sources?${params}`;
  const res = await fetch(url);
  if (!res.ok) {
    const text = await res.text();
    throw new Error(`Failed to fetch sources: ${res.status} - ${text}`);
  }
  return res.json();
}

export async function fetchTeams(
  databaseId: string,
  cursor?: number,
  limit = 100
): Promise<TeamListResponse> {
  const params = new URLSearchParams();
  if (cursor != null) params.append('cursor', String(cursor));
  params.append('limit', String(limit));
  const url = `${API_BASE}/databases/${encodeURIComponent(databaseId)}/teams?${params}`;
  const res = await fetch(url);
  if (!res.ok) {
    const text = await res.text();
    throw new Error(`Failed to fetch teams: ${res.status} - ${text}`);
  }
  return res.json();
}

export async function fetchGameTags(
  databaseId: string,
  cursor?: number,
  limit = 100
): Promise<GameTagListResponse> {
  const params = new URLSearchParams();
  if (cursor != null) params.append('cursor', String(cursor));
  params.append('limit', String(limit));
  const url = `${API_BASE}/databases/${encodeURIComponent(databaseId)}/gametags?${params}`;
  const res = await fetch(url);
  if (!res.ok) {
    const text = await res.text();
    throw new Error(`Failed to fetch game tags: ${res.status} - ${text}`);
  }
  return res.json();
}

function buildEntitySearchParams(request: EntitySearchRequest): URLSearchParams {
  const params = new URLSearchParams();
  Object.entries(request).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      params.append(key, String(value));
    }
  });
  return params;
}

export async function searchPlayers(
  databaseId: string,
  request: EntitySearchRequest
): Promise<EntitySearchResponse<PlayerDto>> {
  const params = buildEntitySearchParams(request);
  const url = `${API_BASE}/databases/${encodeURIComponent(databaseId)}/players/search?${params}`;
  const res = await fetch(url);
  if (!res.ok) {
    const text = await res.text();
    throw new Error(`Search players failed: ${res.status} - ${text}`);
  }
  return res.json();
}

export async function searchTournaments(
  databaseId: string,
  request: EntitySearchRequest
): Promise<EntitySearchResponse<TournamentDto>> {
  const params = buildEntitySearchParams(request);
  const url = `${API_BASE}/databases/${encodeURIComponent(databaseId)}/tournaments/search?${params}`;
  const res = await fetch(url);
  if (!res.ok) {
    const text = await res.text();
    throw new Error(`Search tournaments failed: ${res.status} - ${text}`);
  }
  return res.json();
}

export async function searchAnnotators(
  databaseId: string,
  request: EntitySearchRequest
): Promise<EntitySearchResponse<AnnotatorDto>> {
  const params = buildEntitySearchParams(request);
  const url = `${API_BASE}/databases/${encodeURIComponent(databaseId)}/annotators/search?${params}`;
  const res = await fetch(url);
  if (!res.ok) {
    const text = await res.text();
    throw new Error(`Search annotators failed: ${res.status} - ${text}`);
  }
  return res.json();
}

export async function searchSources(
  databaseId: string,
  request: EntitySearchRequest
): Promise<EntitySearchResponse<SourceDto>> {
  const params = buildEntitySearchParams(request);
  const url = `${API_BASE}/databases/${encodeURIComponent(databaseId)}/sources/search?${params}`;
  const res = await fetch(url);
  if (!res.ok) {
    const text = await res.text();
    throw new Error(`Search sources failed: ${res.status} - ${text}`);
  }
  return res.json();
}

export async function searchTeams(
  databaseId: string,
  request: EntitySearchRequest
): Promise<EntitySearchResponse<TeamDto>> {
  const params = buildEntitySearchParams(request);
  const url = `${API_BASE}/databases/${encodeURIComponent(databaseId)}/teams/search?${params}`;
  const res = await fetch(url);
  if (!res.ok) {
    const text = await res.text();
    throw new Error(`Search teams failed: ${res.status} - ${text}`);
  }
  return res.json();
}

export async function searchGameTags(
  databaseId: string,
  request: EntitySearchRequest
): Promise<EntitySearchResponse<GameTagDto>> {
  const params = buildEntitySearchParams(request);
  const url = `${API_BASE}/databases/${encodeURIComponent(databaseId)}/gametags/search?${params}`;
  const res = await fetch(url);
  if (!res.ok) {
    const text = await res.text();
    throw new Error(`Search game tags failed: ${res.status} - ${text}`);
  }
  return res.json();
}

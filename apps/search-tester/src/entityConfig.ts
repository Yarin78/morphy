import { ApiError, debugSearch, search } from './api/client';
import type {
  EntitySearchResponse,
  FilterOptionsResponse,
  GameSearchResponse,
  QueryPlanDebugInfo,
  RawRecord,
} from './api/types';
import {
  ANNOTATOR_COLUMNS,
  GAME_COLUMNS,
  GAMETAG_COLUMNS,
  PLAYER_COLUMNS,
  SOURCE_COLUMNS,
  TEAM_COLUMNS,
  TOURNAMENT_COLUMNS,
} from './ResultsTable';

type ColumnDef = { key: string; label: string; width: number; render: (row: unknown) => React.ReactNode };

export const ENTITY_TYPES = [
  'Games',
  'Players',
  'Tournaments',
  'Annotators',
  'Sources',
  'Teams',
  'GameTags',
] as const;
export type EntityType = (typeof ENTITY_TYPES)[number];

export const SORT_OPTIONS = ['id', 'date', 'whiteElo', 'blackElo', 'avgElo'] as const;
export const ORDER_OPTIONS = ['asc', 'desc'] as const;
export const RATING_MODES = ['any', 'both', 'white', 'black', 'average', 'difference'] as const;

function sortFieldNames(sortFields: FilterOptionsResponse['sortFields']): string[] {
  return sortFields.map((s) => s.name);
}

/**
 * Returns sort options for the entity: from filterOptions.sortFields when present and non-empty,
 * otherwise from SORT_OPTIONS (Games) or ENTITY_SORT_OPTIONS (other entities).
 * For non-Games entities, "default" is prepended so the UI can use the backend's default index order.
 */
export function getSortOptions(
  entityType: EntityType,
  filterOptions: FilterOptionsResponse | null
): readonly string[] {
  if (entityType === 'Games') {
    return filterOptions?.sortFields?.length
      ? sortFieldNames(filterOptions.sortFields)
      : SORT_OPTIONS;
  }
  const base = filterOptions?.sortFields?.length
    ? sortFieldNames(filterOptions.sortFields)
    : ['id'];
  return ['default', ...base];
}

/**
 * Returns the default sort direction for a sort field from filter options, or 'asc' if unknown.
 */
export function getDefaultSortDirection(
  filterOptions: FilterOptionsResponse | null,
  sortFieldName: string
): 'asc' | 'desc' {
  const option = filterOptions?.sortFields?.find((s) => s.name === sortFieldName);
  const d = option?.defaultDirection?.toLowerCase();
  return d === 'desc' ? 'desc' : 'asc';
}

/**
 * Maps result table column keys to API sort field names (must match FilterOptionsResponse.sortFields[].name).
 * Columns not in the map are not sortable. Used for "sort by column header" in the results table.
 */
export const SORTABLE_COLUMN_MAP: Record<EntityType, Record<string, string>> = {
  Games: {
    id: 'id',
    white: 'whitePlayerName',
    black: 'blackPlayerName',
    result: 'result',
    date: 'playedDate',
    year: 'playedYear',
    eco: 'eco',
    round: 'round',
    tournament: 'tournament',
    source: 'source',
    annotator: 'annotator',
    gameTag: 'gameTag',
    whiteElo: 'whiteElo',
    blackElo: 'blackElo',
    eloAvg: 'eloAvg',
    eloMax: 'eloMax',
    noMoves: 'noMoves',
    whiteTeam: 'whiteTeam',
    blackTeam: 'blackTeam',
    medals: 'medals',
    ait: 'ait',
    vcs: 'vcs',
    setupPosition: 'setupPosition',
    topGame: 'topGame',
    finalMaterial: 'finalMaterial',
    gameVersion: 'gameVersion',
    creationTimestamp: 'creationTimestamp',
    lastChanged: 'lastChanged',
    notation: 'notation',
    lineMoves: 'variationMoves',
  },
  Players: {
    id: 'id',
    lastName: 'lastName',
    firstName: 'firstName',
    gameCount: 'count',
  },
  Tournaments: {
    id: 'id',
    title: 'title',
    startDate: 'startDate',
    endDate: 'endDate',
    place: 'place',
    typeCombined: 'combinedType',
    nation: 'nation',
    category: 'category',
    rounds: 'rounds',
    gameCount: 'count',
    complete: 'complete',
    coordinates: 'coordinates',
    tiebreak: 'tiebreak',
  },
  Annotators: {
    id: 'id',
    name: 'name',
    gameCount: 'count',
  },
  Sources: {
    id: 'id',
    title: 'title',
    publisher: 'publisher',
    date: 'date',
    publication: 'publication',
    version: 'version',
    quality: 'quality',
    gameCount: 'count',
  },
  Teams: {
    id: 'id',
    title: 'title',
    teamNumber: 'number',
    season: 'season',
    year: 'year',
    nation: 'nation',
    gameCount: 'count',
  },
  GameTags: {
    id: 'id',
    title: 'title',
    languages: 'languages',
    languageCount: 'languageCount',
    gameCount: 'count',
  },
};

export interface EntityConfig {
  /** The API path segment of the entity type; also keys its stored UI settings. */
  entityKey: string;
  columns: ColumnDef[];
  countLabel: string;
  emptyMessage: string;
  keyExtractor: (row: { id: number }) => number;
}

function entityConfig(
  entityKey: string,
  columns: unknown,
  countLabel: string,
  emptyMessage: string
): EntityConfig {
  return {
    entityKey,
    columns: columns as ColumnDef[],
    countLabel,
    emptyMessage,
    keyExtractor: (row) => row.id,
  };
}

export const ENTITY_CONFIG: Record<EntityType, EntityConfig> = {
  Games: entityConfig('games', GAME_COLUMNS, 'games', 'No games match the search criteria.'),
  Players: entityConfig('players', PLAYER_COLUMNS, 'players', 'No players found.'),
  Tournaments: entityConfig('tournaments', TOURNAMENT_COLUMNS, 'tournaments', 'No tournaments found.'),
  Annotators: entityConfig('annotators', ANNOTATOR_COLUMNS, 'annotators', 'No annotators found.'),
  Sources: entityConfig('sources', SOURCE_COLUMNS, 'sources', 'No sources found.'),
  Teams: entityConfig('teams', TEAM_COLUMNS, 'teams', 'No teams found.'),
  GameTags: entityConfig('gametags', GAMETAG_COLUMNS, 'game tags', 'No game tags found.'),
};

/** What a search returned, whatever the entity type. */
export interface SearchOutcome {
  data: unknown[];
  count: number;
  executionTimeMs?: number;
  /** The query plans; absent when the database has no diagnostics. */
  debugInfo?: QueryPlanDebugInfo;
  /** The raw records of every returned item, keyed by its id; absent without diagnostics. */
  raw?: Record<string, RawRecord[]>;
  rawResponse: unknown;
}

type SearchResponse = GameSearchResponse | EntitySearchResponse<unknown>;

function rows(response: SearchResponse): unknown[] {
  return 'games' in response ? response.games : response.items;
}

/**
 * Searches games or an entity type through the debug endpoint, which also returns the query plans
 * and raw records. A database without diagnostics (501) gets the normal search instead.
 *
 * @param request the search parameters: a GameSearchRequest for Games, an EntitySearchRequest
 *     otherwise
 */
export async function runSearch(
  entityType: EntityType,
  databaseId: string,
  request: object,
  executeAllPlans: boolean
): Promise<SearchOutcome> {
  const path = ENTITY_CONFIG[entityType].entityKey;
  try {
    const res = await debugSearch<SearchResponse>(databaseId, path, request, executeAllPlans);
    return {
      data: rows(res.result),
      count: res.result.count,
      executionTimeMs: res.result.metadata.executionTimeMs,
      debugInfo: res.plans,
      raw: res.raw,
      rawResponse: res,
    };
  } catch (err) {
    if (!(err instanceof ApiError && err.status === 501)) throw err;
    const res = await search<SearchResponse>(databaseId, path, request);
    return {
      data: rows(res),
      count: res.count,
      executionTimeMs: res.metadata.executionTimeMs,
      rawResponse: res,
    };
  }
}

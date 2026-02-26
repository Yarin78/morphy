import {
  searchAnnotators,
  searchGameTags,
  searchGames,
  searchPlayers,
  searchSources,
  searchTeams,
  searchTournaments,
} from './api/client';
import type { EntitySearchRequest, GameSearchRequest } from './api/types';
import type { FilterOptionsResponse } from './api/types';
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
  Games: { id: 'id', white: 'whiteElo', black: 'blackElo', date: 'date' },
  Players: { id: 'id', lastName: 'lastName', firstName: 'firstName', gameCount: 'count' },
  Tournaments: { id: 'id', title: 'title', startDate: 'startDate', endDate: 'endDate', place: 'place', typeCombined: 'combinedType', nation: 'nation', category: 'category', rounds: 'rounds', gameCount: 'count', complete: 'complete', coordinates: 'coordinates', tiebreak: 'tiebreak' },
  Annotators: { id: 'id', name: 'name', gameCount: 'count' },
  Sources: { id: 'id', title: 'title', publisher: 'publisher', date: 'date', publication: 'publication', version: 'version', quality: 'quality', gameCount: 'count' },
  Teams: { id: 'id', title: 'title', teamNumber: 'number', season: 'season', year: 'year', nation: 'nation', gameCount: 'count' },
  GameTags: {
    id: 'id',
    title: 'title',
    languages: 'languages',
    languageCount: 'languageCount',
    gameCount: 'count',
  },
};

type FetchOpts = {
  gameRequest?: GameSearchRequest;
  entitySearchRequest?: EntitySearchRequest;
};

export interface EntityConfig {
  entityKey: string;
  columns: ColumnDef[];
  countLabel: string;
  emptyMessage: string;
  keyExtractor: (row: { id: number }) => number;
  fetch: (
    db: string,
    opts: FetchOpts
  ) => Promise<{
    data: unknown[];
    count: number;
    metadata?: { executionTimeMs?: number };
    debugInfo?: unknown;
    rawResponse: unknown;
  }>;
}

export const ENTITY_CONFIG: Record<EntityType, EntityConfig> = {
  Games: {
    entityKey: 'games',
    columns: GAME_COLUMNS as ColumnDef[],
    countLabel: 'games',
    emptyMessage: 'No games match the search criteria.',
    keyExtractor: (g) => g.id,
    fetch: async (db, opts) => {
      const res = await searchGames(db, opts.gameRequest!);
      return {
        data: res.games,
        count: res.count,
        metadata: res.metadata,
        debugInfo: res.debugInfo,
        rawResponse: res,
      };
    },
  },
  Players: {
    entityKey: 'players',
    columns: PLAYER_COLUMNS as ColumnDef[],
    countLabel: 'players',
    emptyMessage: 'No players found.',
    keyExtractor: (p) => p.id,
    fetch: async (db, opts) => {
      const req = opts.entitySearchRequest ?? {};
      const res = await searchPlayers(db, {
        filter: req.filter,
        sortBy: req.sortBy ?? '+id',
        debugQueryPlans: req.debugQueryPlans,
        debugExecuteAllPlans: req.debugExecuteAllPlans,
        debugRawData: req.debugRawData,
      });
      return {
        data: res.items,
        count: res.count,
        metadata: { executionTimeMs: res.metadata.executionTimeMs },
        debugInfo: res.debugInfo,
        rawResponse: res,
      };
    },
  },
  Tournaments: {
    entityKey: 'tournaments',
    columns: TOURNAMENT_COLUMNS as ColumnDef[],
    countLabel: 'tournaments',
    emptyMessage: 'No tournaments found.',
    keyExtractor: (t) => t.id,
    fetch: async (db, opts) => {
      const req = opts.entitySearchRequest ?? {};
      const res = await searchTournaments(db, {
        filter: req.filter,
        sortBy: req.sortBy ?? '+id',
        debugQueryPlans: req.debugQueryPlans,
        debugExecuteAllPlans: req.debugExecuteAllPlans,
        debugRawData: req.debugRawData,
      });
      return {
        data: res.items,
        count: res.count,
        metadata: { executionTimeMs: res.metadata.executionTimeMs },
        debugInfo: res.debugInfo,
        rawResponse: res,
      };
    },
  },
  Annotators: {
    entityKey: 'annotators',
    columns: ANNOTATOR_COLUMNS as ColumnDef[],
    countLabel: 'annotators',
    emptyMessage: 'No annotators found.',
    keyExtractor: (a) => a.id,
    fetch: async (db, opts) => {
      const req = opts.entitySearchRequest ?? {};
      const res = await searchAnnotators(db, {
        filter: req.filter,
        sortBy: req.sortBy ?? '+id',
        debugQueryPlans: req.debugQueryPlans,
        debugExecuteAllPlans: req.debugExecuteAllPlans,
        debugRawData: req.debugRawData,
      });
      return {
        data: res.items,
        count: res.count,
        metadata: { executionTimeMs: res.metadata.executionTimeMs },
        debugInfo: res.debugInfo,
        rawResponse: res,
      };
    },
  },
  Sources: {
    entityKey: 'sources',
    columns: SOURCE_COLUMNS as ColumnDef[],
    countLabel: 'sources',
    emptyMessage: 'No sources found.',
    keyExtractor: (s) => s.id,
    fetch: async (db, opts) => {
      const req = opts.entitySearchRequest ?? {};
      const res = await searchSources(db, {
        filter: req.filter,
        sortBy: req.sortBy ?? '+id',
        debugQueryPlans: req.debugQueryPlans,
        debugExecuteAllPlans: req.debugExecuteAllPlans,
        debugRawData: req.debugRawData,
      });
      return {
        data: res.items,
        count: res.count,
        metadata: { executionTimeMs: res.metadata.executionTimeMs },
        debugInfo: res.debugInfo,
        rawResponse: res,
      };
    },
  },
  Teams: {
    entityKey: 'teams',
    columns: TEAM_COLUMNS as ColumnDef[],
    countLabel: 'teams',
    emptyMessage: 'No teams found.',
    keyExtractor: (t) => t.id,
    fetch: async (db, opts) => {
      const req = opts.entitySearchRequest ?? {};
      const res = await searchTeams(db, {
        filter: req.filter,
        sortBy: req.sortBy ?? '+id',
        debugQueryPlans: req.debugQueryPlans,
        debugExecuteAllPlans: req.debugExecuteAllPlans,
        debugRawData: req.debugRawData,
      });
      return {
        data: res.items,
        count: res.count,
        metadata: { executionTimeMs: res.metadata.executionTimeMs },
        debugInfo: res.debugInfo,
        rawResponse: res,
      };
    },
  },
  GameTags: {
    entityKey: 'gametags',
    columns: GAMETAG_COLUMNS as ColumnDef[],
    countLabel: 'game tags',
    emptyMessage: 'No game tags found.',
    keyExtractor: (g) => g.id,
    fetch: async (db, opts) => {
      const req = opts.entitySearchRequest ?? {};
      const res = await searchGameTags(db, {
        filter: req.filter,
        sortBy: req.sortBy ?? '+id',
        debugQueryPlans: req.debugQueryPlans,
        debugExecuteAllPlans: req.debugExecuteAllPlans,
        debugRawData: req.debugRawData,
      });
      return {
        data: res.items,
        count: res.count,
        metadata: { executionTimeMs: res.metadata.executionTimeMs },
        debugInfo: res.debugInfo,
        rawResponse: res,
      };
    },
  },
};

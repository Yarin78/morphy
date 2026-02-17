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
import {
  ANNOTATOR_COLUMNS,
  GAME_COLUMNS,
  GAMETAG_COLUMNS,
  PLAYER_COLUMNS,
  SOURCE_COLUMNS,
  TEAM_COLUMNS,
  TOURNAMENT_COLUMNS,
} from './ResultsTable';

type ColumnDef = { key: string; label: string; render: (row: unknown) => React.ReactNode };

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

/** Sort options per entity type (id, name/title, date where applicable). */
export const ENTITY_SORT_OPTIONS: Record<Exclude<EntityType, 'Games'>, readonly string[]> = {
  Players: ['id', 'name'],
  Tournaments: ['id', 'name', 'date'],
  Annotators: ['id', 'name'],
  Sources: ['id', 'name'],
  Teams: ['id', 'name'],
  GameTags: ['id', 'name'],
};

type FetchOpts = {
  limit: number;
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
        offset: req.offset ?? 0,
        limit: req.limit ?? opts.limit,
        sortBy: req.sortBy ?? 'id',
        order: req.order ?? 'asc',
      });
      return {
        data: res.items,
        count: res.count,
        metadata: { executionTimeMs: res.metadata.executionTimeMs },
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
        offset: req.offset ?? 0,
        limit: req.limit ?? opts.limit,
        sortBy: req.sortBy ?? 'id',
        order: req.order ?? 'asc',
      });
      return {
        data: res.items,
        count: res.count,
        metadata: { executionTimeMs: res.metadata.executionTimeMs },
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
        offset: req.offset ?? 0,
        limit: req.limit ?? opts.limit,
        sortBy: req.sortBy ?? 'id',
        order: req.order ?? 'asc',
      });
      return {
        data: res.items,
        count: res.count,
        metadata: { executionTimeMs: res.metadata.executionTimeMs },
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
        offset: req.offset ?? 0,
        limit: req.limit ?? opts.limit,
        sortBy: req.sortBy ?? 'id',
        order: req.order ?? 'asc',
      });
      return {
        data: res.items,
        count: res.count,
        metadata: { executionTimeMs: res.metadata.executionTimeMs },
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
        offset: req.offset ?? 0,
        limit: req.limit ?? opts.limit,
        sortBy: req.sortBy ?? 'id',
        order: req.order ?? 'asc',
      });
      return {
        data: res.items,
        count: res.count,
        metadata: { executionTimeMs: res.metadata.executionTimeMs },
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
        offset: req.offset ?? 0,
        limit: req.limit ?? opts.limit,
        sortBy: req.sortBy ?? 'id',
        order: req.order ?? 'asc',
      });
      return {
        data: res.items,
        count: res.count,
        metadata: { executionTimeMs: res.metadata.executionTimeMs },
        rawResponse: res,
      };
    },
  },
};

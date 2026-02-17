import {
  fetchAnnotators,
  fetchGameTags,
  fetchPlayers,
  fetchSources,
  fetchTeams,
  fetchTournaments,
  searchGames,
} from './api/client';
import type { GameSearchRequest } from './api/types';
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

type FetchOpts = { limit: number; gameRequest?: GameSearchRequest };

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
      const res = await fetchPlayers(db, undefined, opts.limit);
      return { data: res.players, count: res.count, rawResponse: res };
    },
  },
  Tournaments: {
    entityKey: 'tournaments',
    columns: TOURNAMENT_COLUMNS as ColumnDef[],
    countLabel: 'tournaments',
    emptyMessage: 'No tournaments found.',
    keyExtractor: (t) => t.id,
    fetch: async (db, opts) => {
      const res = await fetchTournaments(db, undefined, opts.limit);
      return { data: res.tournaments, count: res.count, rawResponse: res };
    },
  },
  Annotators: {
    entityKey: 'annotators',
    columns: ANNOTATOR_COLUMNS as ColumnDef[],
    countLabel: 'annotators',
    emptyMessage: 'No annotators found.',
    keyExtractor: (a) => a.id,
    fetch: async (db, opts) => {
      const res = await fetchAnnotators(db, undefined, opts.limit);
      return { data: res.annotators, count: res.count, rawResponse: res };
    },
  },
  Sources: {
    entityKey: 'sources',
    columns: SOURCE_COLUMNS as ColumnDef[],
    countLabel: 'sources',
    emptyMessage: 'No sources found.',
    keyExtractor: (s) => s.id,
    fetch: async (db, opts) => {
      const res = await fetchSources(db, undefined, opts.limit);
      return { data: res.sources, count: res.count, rawResponse: res };
    },
  },
  Teams: {
    entityKey: 'teams',
    columns: TEAM_COLUMNS as ColumnDef[],
    countLabel: 'teams',
    emptyMessage: 'No teams found.',
    keyExtractor: (t) => t.id,
    fetch: async (db, opts) => {
      const res = await fetchTeams(db, undefined, opts.limit);
      return { data: res.teams, count: res.count, rawResponse: res };
    },
  },
  GameTags: {
    entityKey: 'gametags',
    columns: GAMETAG_COLUMNS as ColumnDef[],
    countLabel: 'game tags',
    emptyMessage: 'No game tags found.',
    keyExtractor: (g) => g.id,
    fetch: async (db, opts) => {
      const res = await fetchGameTags(db, undefined, opts.limit);
      return { data: res.gameTags, count: res.count, rawResponse: res };
    },
  },
};

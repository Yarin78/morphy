import type { ReactNode } from 'react';
import type { GameDto, PlayerDto } from '../api/types';
import { ENTITY_CONFIG, type EntityType, SORTABLE_COLUMN_MAP } from '../database/entityConfig';
import { SEARCH_KIND_SINGULAR, type SearchKind } from './queries';

// The columns of the search results, taken from the search tester's.

export interface Column {
  key: string;
  label: string;
  width: number;
  render: (row: unknown) => ReactNode;
}

const ENTITY_TYPES: Record<SearchKind, EntityType> = {
  games: 'Games',
  players: 'Players',
  tournaments: 'Tournaments',
  annotators: 'Annotators',
  sources: 'Sources',
  teams: 'Teams',
  gametags: 'GameTags',
};

export function toEntityType(kind: SearchKind): EntityType {
  return ENTITY_TYPES[kind];
}

// The columns of a game shown until others are picked
const DEFAULT_GAME_COLUMNS = ['id', 'white', 'whiteElo', 'black', 'blackElo', 'result', 'tournament', 'round', 'date', 'notation'];

// The games of a board's position, from a reference database, where a game's number in it and
// its round say little
const DEFAULT_POSITION_GAME_COLUMNS = DEFAULT_GAME_COLUMNS.filter((key) => key !== 'id' && key !== 'round');

/**
 * The lists of results whose columns are picked apart: those of each kind, and the games of a
 * board's position, which have the columns of games.
 */
export type ColumnSet = SearchKind | 'positionGames';

/** The kind of results a list of results has the columns of. */
export function kindOfSet(set: ColumnSet): SearchKind {
  return set === 'positionGames' ? 'games' : set;
}

// Narrower than the search tester's, as the results share the screen with the preview
const GAME_WIDTHS: Record<string, number> = {
  id: 56,
  white: 150,
  whiteElo: 54,
  black: 150,
  blackElo: 54,
  result: 52,
  noMoves: 54,
  eco: 46,
  tournament: 180,
  round: 54,
  date: 100,
  notation: 300,
};

// Shorter than the search tester's headers
const GAME_LABELS: Record<string, string> = { id: '#' };

/** The column of a game's first moves, which are fetched only while it's shown. */
export const NOTATION_COLUMN = 'notation';

export function entityColumns(type: EntityType): Column[] {
  return ENTITY_CONFIG[type].columns as Column[];
}

export interface ColumnOptions {
  /** Players by their full names, "Carlsen, Magnus", not "Carlsen, M" */
  fullPlayerNames: boolean;
}

/** A player as named in the results: "Carlsen, M", or in full. */
function playerName(player: PlayerDto | undefined, full: boolean): string {
  const last = player?.lastName ?? '';
  const first = player?.firstName;
  if (!last || !first) return last;
  return `${last}, ${full ? first : first.charAt(0)}`;
}

/** The players' columns of the games, named as the options say; a text has its title as White. */
function gameRender(key: string, options: ColumnOptions): ((row: unknown) => ReactNode) | undefined {
  if (key === 'white') {
    return (row) => {
      const g = row as GameDto;
      return g.type === 'text' ? (g.textTitle ?? 'Text') : playerName(g.whitePlayer, options.fullPlayerNames);
    };
  }
  if (key === 'black') return (row) => playerName((row as GameDto).blackPlayer, options.fullPlayerNames);
  return undefined;
}

/** All the columns of a kind's results, the ones shown or not. */
export function columnsOf(kind: SearchKind, options: ColumnOptions): Column[] {
  if (kind !== 'games') return entityColumns(ENTITY_TYPES[kind]);
  return entityColumns('Games').map((c) => ({
    ...c,
    label: GAME_LABELS[c.key] ?? c.label,
    width: GAME_WIDTHS[c.key] ?? c.width,
    render: gameRender(c.key, options) ?? c.render,
  }));
}

/** The keys of the columns of a list of results shown until others are picked. */
export function defaultColumnsOf(kind: ColumnSet): string[] {
  if (kind === 'positionGames') return DEFAULT_POSITION_GAME_COLUMNS;
  if (kind === 'games') return DEFAULT_GAME_COLUMNS;
  // An entity's id is there to be picked, not shown at first
  return entityColumns(ENTITY_TYPES[kind])
    .map((c) => c.key)
    .filter((key) => key !== 'id');
}

// The fields the games of a position can be sorted on, from the facts its index keeps of them
const POSITION_GAME_SORT_FIELDS = new Set(['id', 'playedDate', 'playedYear', 'whiteElo', 'blackElo', 'eloAvg', 'eloMax']);

/** The sort field of a column of a list of results, if it can be sorted on. */
export function sortFieldOf(set: ColumnSet, columnKey: string): string | undefined {
  const field = SORTABLE_COLUMN_MAP[ENTITY_TYPES[kindOfSet(set)]][columnKey];
  return set === 'positionGames' && field && !POSITION_GAME_SORT_FIELDS.has(field) ? undefined : field;
}

// The sort fields sorted with the largest or latest first, unless sorted again
const DESCENDING = new Set([
  'playedDate',
  'playedYear',
  'whiteElo',
  'blackElo',
  'eloAvg',
  'eloMax',
  'noMoves',
  'count',
  'startDate',
  'endDate',
  'date',
  'category',
  'rounds',
  'year',
]);

export function defaultOrderOf(field: string): 'asc' | 'desc' {
  return DESCENDING.has(field) ? 'desc' : 'asc';
}

/** An entity's name, as its preview is titled. */
export function entityTitle(kind: SearchKind, entity: Record<string, unknown>): string {
  if (kind === 'players') {
    const last = (entity.lastName as string | undefined) ?? '';
    const first = (entity.firstName as string | undefined) ?? '';
    return first ? `${last}, ${first}` : last;
  }
  return String(entity.title ?? entity.name ?? '');
}

/** What an entity is, as the games limited to it are labelled: "Player: Kasparov, Garry". */
export function entityLabel(kind: SearchKind, entity: Record<string, unknown>): string {
  return `${SEARCH_KIND_SINGULAR[kind]}: ${entityTitle(kind, entity)}`;
}

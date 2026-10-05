import type { ReactNode } from 'react';
import { ENTITY_CONFIG, type EntityType, SORTABLE_COLUMN_MAP } from '../database/entityConfig';
import { SEARCH_KIND_SINGULAR, type SearchKind } from './queries';

// The columns of the search results, taken from the search tester's: all of an entity's, and
// the main ones of a game's.

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

const GAME_COLUMN_KEYS = ['id', 'white', 'whiteElo', 'black', 'blackElo', 'result', 'noMoves', 'eco', 'tournament', 'date'];

// Narrower than the search tester's, as the results share the screen with the preview
const GAME_WIDTHS: Record<string, number> = {
  id: 56,
  white: 150,
  whiteElo: 50,
  black: 150,
  blackElo: 50,
  result: 48,
  noMoves: 50,
  eco: 46,
  tournament: 180,
  date: 90,
};

const GAME_LABELS: Record<string, string> = { id: '#', whiteElo: 'Elo', blackElo: 'Elo' };

export function entityColumns(type: EntityType): Column[] {
  return ENTITY_CONFIG[type].columns as Column[];
}

/** The columns of a kind's results. */
export function columnsOf(kind: SearchKind): Column[] {
  if (kind !== 'games') return entityColumns(ENTITY_TYPES[kind]);
  const all = entityColumns('Games');
  return GAME_COLUMN_KEYS.map((key) => all.find((c) => c.key === key)!).map((c) => ({
    ...c,
    label: GAME_LABELS[c.key] ?? c.label,
    width: GAME_WIDTHS[c.key] ?? c.width,
  }));
}

/** The sort field of a column of a kind's results, if it can be sorted on. */
export function sortFieldOf(kind: SearchKind, columnKey: string): string | undefined {
  return SORTABLE_COLUMN_MAP[ENTITY_TYPES[kind]][columnKey];
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

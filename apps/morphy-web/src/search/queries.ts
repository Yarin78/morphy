import { NATIONS } from 'game-view';

// The search forms of a database document, and the filter queries they make in the search
// language of morphy-service (see morphy-service/docs/SEARCH_GUIDE.md).

/** What a search can find: games, or one kind of entity. */
export const SEARCH_KINDS = ['games', 'players', 'tournaments', 'annotators', 'sources', 'teams', 'gametags'] as const;
export type SearchKind = (typeof SEARCH_KINDS)[number];

export const SEARCH_KIND_LABELS: Record<SearchKind, string> = {
  games: 'Games',
  players: 'Players',
  tournaments: 'Events',
  annotators: 'Annotators',
  sources: 'Sources',
  teams: 'Teams',
  gametags: 'Tags',
};

/** One of a kind, as a thing found is called: "Player". */
export const SEARCH_KIND_SINGULAR: Record<SearchKind, string> = {
  games: 'Game',
  players: 'Player',
  tournaments: 'Event',
  annotators: 'Annotator',
  sources: 'Source',
  teams: 'Team',
  gametags: 'Game Tag',
};

/** The time controls of a tournament, by their names in the search language. */
export const TIME_CONTROLS = [
  { id: 'normal', label: 'Classical' },
  { id: 'rapid', label: 'Rapid' },
  { id: 'blitz', label: 'Blitz' },
] as const;
export type TimeControl = (typeof TIME_CONTROLS)[number]['id'];

export const RESULTS = [
  { id: '', label: 'Any' },
  { id: '1-0', label: '1-0' },
  { id: '0-1', label: '0-1' },
  { id: 'draw', label: '½-½' },
] as const;

export const RATING_MODES = [
  { id: 'any', label: 'Either player' },
  { id: 'both', label: 'Both players' },
  { id: 'average', label: 'Average' },
] as const;
export type RatingMode = (typeof RATING_MODES)[number]['id'];

/** An entity the games are limited to, picked from an entity search: playerid:12 and such. */
export interface EntityConstraint {
  /** The id field of the search language, e.g. playerid */
  field: string;
  id: number;
  /** What the entity is, as shown: "Player: Kasparov, Garry" */
  label: string;
}

export interface GameForm {
  white: string;
  black: string;
  /** The players in either colour: white and black are then a player and an opponent */
  eitherColour: boolean;
  dateFrom: string;
  dateTo: string;
  timeControls: TimeControl[];
  // The advanced filters
  result: string;
  eco: string;
  ratingMin: string;
  ratingMax: string;
  ratingMode: RatingMode;
  tournament: string;
  /** The place of the event */
  site: string;
  entity: EntityConstraint | null;
}

export const EMPTY_GAME_FORM: GameForm = {
  white: '',
  black: '',
  eitherColour: true,
  dateFrom: '',
  dateTo: '',
  timeControls: [],
  result: '',
  eco: '',
  ratingMin: '',
  ratingMax: '',
  ratingMode: 'any',
  tournament: '',
  site: '',
  entity: null,
};

/** The form of an entity search: a name (the kind's main field) and, for tournaments, more. */
export interface EntityForm {
  name: string;
  /** The tournament's place, shown as its site */
  place: string;
  dateFrom: string;
  dateTo: string;
  timeControls: TimeControl[];
  // The advanced filters of a tournament
  /** One of game-view's TOURNAMENT_TYPES, by its value: tourn, swiss, ...; '' for any */
  type: string;
  /** A nation's name or IOC code, as typed or picked; '' for any */
  nation: string;
  categoryMin: string;
  categoryMax: string;
}

export const EMPTY_ENTITY_FORM: EntityForm = {
  name: '',
  place: '',
  dateFrom: '',
  dateTo: '',
  timeControls: [],
  type: '',
  nation: '',
  categoryMin: '',
  categoryMax: '',
};

/** Whether an entity form has any of a tournament's advanced filters set. */
export function hasAdvancedEntityFilters(form: EntityForm): boolean {
  return !!(form.type || form.nation.trim() || form.categoryMin.trim() || form.categoryMax.trim());
}

/** Whether a game form has any of its advanced filters set. */
export function hasAdvancedFilters(form: GameForm): boolean {
  return !!(
    form.result ||
    form.eco.trim() ||
    form.ratingMin.trim() ||
    form.ratingMax.trim() ||
    form.tournament.trim() ||
    form.site.trim()
  );
}

/** A value as the search language reads it: quoted if it has spaces, commas or quotes. */
export function quote(value: string): string {
  const v = value.trim();
  return /[\s,"]/.test(v) ? `"${v.replace(/"/g, '')}"` : v;
}

/** Whether a date can be searched for: a year, a year and month, or a full date. */
export function isValidDate(value: string): boolean {
  return value.trim() === '' || /^\d{4}(-\d{1,2}(-\d{1,2})?)?$/.test(value.trim());
}

/** Whether a rating can be searched for: a number up to 4 digits. */
export function isValidRating(value: string): boolean {
  return value.trim() === '' || /^\d{1,4}$/.test(value.trim());
}

/** The IOC code of the nation a text names, by its name or code; undefined if none. */
export function nationCode(text: string): string | undefined {
  const t = text.trim().toLowerCase();
  if (!t) return undefined;
  return NATIONS.find((n) => n.ioc.toLowerCase() === t || n.name.toLowerCase() === t)?.ioc;
}

/** Whether a nation can be searched for: none, or one named by its name or code. */
export function isValidNation(text: string): boolean {
  return text.trim() === '' || nationCode(text) !== undefined;
}

function nationCondition(text: string): string | null {
  const ioc = nationCode(text);
  return ioc ? `nation:${ioc}` : null;
}

/** Whether a tournament's category can be searched for: a number up to 2 digits. */
export function isValidCategory(value: string): boolean {
  return value.trim() === '' || /^\d{1,2}$/.test(value.trim());
}

/**
 * The category condition of a range, either end of which may be open; null for none. Always a
 * range, as a single value is the least category in a v1 database but the exact one in a v2. A
 * range up to a category starts at 1, leaving out the tournaments without one, stored as 0.
 */
function categoryCondition(min: string, max: string): string | null {
  const hi = isValidCategory(max) ? max.trim() : '';
  const lo = (isValidCategory(min) ? min.trim() : '') || (hi ? '1' : '');
  if (!lo && !hi) return null;
  return `category:${lo}..${hi}`;
}

/** The date condition on a field for a range, either end of which may be open; null for none. */
function dateCondition(field: string, from: string, to: string): string | null {
  const f = isValidDate(from) ? from.trim() : '';
  const t = isValidDate(to) ? to.trim() : '';
  if (!f && !t) return null;
  if (f === t) return `${field}:${f}`;
  return `${field}:${f}..${t}`;
}

/** The time control condition on a field; none when no time control or all of them are picked. */
function timeCondition(field: string, timeControls: TimeControl[]): string | null {
  if (timeControls.length === 0 || timeControls.length === TIME_CONTROLS.length) return null;
  const picked = TIME_CONTROLS.filter((tc) => timeControls.includes(tc.id)).map((tc) => tc.id);
  return `${field}:${picked.join('|')}`;
}

/** The conditions on the players: by colour, or the two in either colour. */
function playerConditions(form: GameForm): string[] {
  const white = form.white.trim();
  const black = form.black.trim();
  if (!form.eitherColour) {
    return [white && `white:${quote(white)}`, black && `black:${quote(black)}`].filter(Boolean) as string[];
  }
  if (white && black) {
    // Both players match one of the names. The language has no OR, so this also finds a game
    // between two players matching the same name, which a name precise enough rules out.
    return [`player.name:${quote(`${white}|${black}`)},position=both`];
  }
  const one = white || black;
  return one ? [`player:${quote(one)}`] : [];
}

/** The filter query of a game form; empty for every game. */
export function gameQuery(form: GameForm): string {
  const conditions: (string | null)[] = [
    ...playerConditions(form),
    dateCondition('date', form.dateFrom, form.dateTo),
    timeCondition('tournament.time', form.timeControls),
    form.result ? `result:${form.result}` : null,
    form.eco.trim() ? `eco:${quote(form.eco.toUpperCase())}` : null,
    ratingCondition(form),
    form.tournament.trim() ? `tournament:${quote(form.tournament)}` : null,
    form.site.trim() ? `tournament.place:${quote(form.site)}` : null,
    form.entity ? `${form.entity.field}:${form.entity.id}` : null,
  ];
  return conditions.filter(Boolean).join(' ');
}

function ratingCondition(form: GameForm): string | null {
  const min = isValidRating(form.ratingMin) ? form.ratingMin.trim() : '';
  const max = isValidRating(form.ratingMax) ? form.ratingMax.trim() : '';
  if (!min && !max) return null;
  const range = min === max ? min : `${min}..${max}`;
  return form.ratingMode === 'any' ? `rating:${range}` : `rating:${range},mode=${form.ratingMode}`;
}

/** The filter query of an entity form; empty for every entity of the kind. */
export function entityQuery(kind: Exclude<SearchKind, 'games'>, form: EntityForm): string {
  // A bare value is a condition on the kind's main field: a name or a title
  const conditions: (string | null)[] = [form.name.trim() ? quote(form.name) : null];
  if (kind === 'tournaments') {
    conditions.push(
      form.place.trim() ? `place:${quote(form.place)}` : null,
      dateCondition('date', form.dateFrom, form.dateTo),
      timeCondition('time', form.timeControls),
      form.type ? `type:${form.type}` : null,
      nationCondition(form.nation),
      categoryCondition(form.categoryMin, form.categoryMax)
    );
  }
  return conditions.filter(Boolean).join(' ');
}

/** The label of an entity kind's main field, in its search form. */
export function nameLabel(kind: Exclude<SearchKind, 'games'>): string {
  return kind === 'players' || kind === 'annotators' ? 'Name' : 'Title';
}

/** The id field games are found by for an entity kind, e.g. playerid. */
export const ENTITY_ID_FIELDS: Record<Exclude<SearchKind, 'games'>, string> = {
  players: 'playerid',
  tournaments: 'tournamentid',
  annotators: 'annotatorid',
  sources: 'sourceid',
  teams: 'teamid',
  gametags: 'gametagid',
};

/** A date whose parts may be unknown: 0 means not set. */
export interface DateParts {
  year: number;
  month: number;
  day: number;
}

/**
 * A game's tournament, as edited in the Edit Game Info dialog. With an id, it's an existing
 * tournament in the database, shared with every other game in it; without one, it's a new
 * tournament, created (or matched to an identical one) when the game is saved.
 */
export interface TournamentInfo {
  id: number | null;
  title: string;
  startDate?: DateParts;
  endDate?: DateParts;
  place?: string;
  /** An IOC code, e.g. 'NOR'. */
  nation?: string;
  /** One of TOURNAMENT_TYPES. */
  type?: string;
  /** One of TIME_CONTROLS; unset means normal. */
  timeControl?: string;
  rounds?: number;
  category?: number;
  complete?: boolean;
  teamTournament?: boolean;
  /** The number of games in an existing tournament. */
  gameCount?: number;
}

/**
 * How the Edit Game Info dialog finds and changes existing tournaments. Without one, the
 * tournament is just a name.
 */
export interface TournamentService {
  /** Tournaments whose title starts with the text, newest first. */
  search(text: string): Promise<TournamentInfo[]>;
  get(id: number): Promise<TournamentInfo>;
  /** Changes an existing tournament, for every game in it, and returns it as saved. */
  update(tournament: TournamentInfo): Promise<TournamentInfo>;
}

export const TOURNAMENT_TYPES: { value: string; label: string }[] = [
  { value: 'tourn', label: 'Tournament' },
  { value: 'swiss', label: 'Open' },
  { value: 'match', label: 'Match' },
  { value: 'team', label: 'Team' },
  { value: 'k.o.', label: 'Knockout' },
  { value: 'simul', label: 'Simul' },
  { value: 'schev', label: 'Scheveningen' },
  { value: 'game', label: 'Game' },
];

export const TIME_CONTROLS: { value: string; label: string }[] = [
  { value: 'blitz', label: 'Blitz' },
  { value: 'rapid', label: 'Rapid' },
  { value: 'corr', label: 'Correspondence' },
];

/**
 * The PGN tags holding the tournament on the Chess instance. Event, Site and EventDate are the
 * usual PGN tags; the others are only understood by whoever saves the game.
 */
export const TOURNAMENT_TAGS = {
  id: 'EventId',
  title: 'Event',
  place: 'Site',
  startDate: 'EventDate',
  endDate: 'EventEndDate',
  nation: 'EventCountry',
  type: 'EventType',
  timeControl: 'EventTimeControl',
  rounds: 'EventRounds',
  category: 'EventCategory',
  complete: 'EventComplete',
  teamTournament: 'EventTeam',
} as const;

export function formatDateTag(date: DateParts | undefined): string {
  const part = (value: number | undefined, width: number) =>
    value ? String(value).padStart(width, '0') : '?'.repeat(width);
  return `${part(date?.year, 4)}.${part(date?.month, 2)}.${part(date?.day, 2)}`;
}

/** A 'yyyy.mm.dd' tag, with '?' for unknown parts; undefined if no part is known. */
export function parseDateTag(value: string): DateParts | undefined {
  const [y, m, d] = value.split('.').map((part) => (/^\d+$/.test(part ?? '') ? parseInt(part, 10) : 0));
  const date = { year: y ?? 0, month: m ?? 0, day: d ?? 0 };
  return date.year || date.month || date.day ? date : undefined;
}

/** The tags for a tournament: every tag in TOURNAMENT_TAGS, '' for those not set. */
export function tournamentToTags(t: TournamentInfo | null): Record<string, string> {
  const hasDate = (date?: DateParts) => Boolean(date && (date.year || date.month || date.day));
  return {
    [TOURNAMENT_TAGS.id]: t?.id != null ? String(t.id) : '',
    [TOURNAMENT_TAGS.title]: t?.title ?? '',
    [TOURNAMENT_TAGS.place]: t?.place ?? '',
    [TOURNAMENT_TAGS.startDate]: hasDate(t?.startDate) ? formatDateTag(t?.startDate) : '',
    [TOURNAMENT_TAGS.endDate]: hasDate(t?.endDate) ? formatDateTag(t?.endDate) : '',
    [TOURNAMENT_TAGS.nation]: t?.nation ?? '',
    [TOURNAMENT_TAGS.type]: t?.type ?? '',
    [TOURNAMENT_TAGS.timeControl]: t?.timeControl ?? '',
    [TOURNAMENT_TAGS.rounds]: t?.rounds ? String(t.rounds) : '',
    [TOURNAMENT_TAGS.category]: t?.category ? String(t.category) : '',
    [TOURNAMENT_TAGS.complete]: t?.complete ? '1' : '',
    [TOURNAMENT_TAGS.teamTournament]: t?.teamTournament ? '1' : '',
  };
}

/** Reads a tournament from its tags; null if there is none. */
export function tournamentFromTags(tag: (name: string) => string): TournamentInfo | null {
  const text = (name: string) => {
    const value = tag(name).trim();
    return value && !/^\?+$/.test(value) ? value : undefined;
  };
  const number = (name: string) => {
    const value = text(name);
    return value && /^\d+$/.test(value) && +value > 0 ? +value : undefined;
  };
  const id = text(TOURNAMENT_TAGS.id);
  const title = text(TOURNAMENT_TAGS.title) ?? '';
  if (id === undefined && !title) return null;
  return {
    id: id !== undefined && /^\d+$/.test(id) ? +id : null,
    title,
    startDate: parseDateTag(tag(TOURNAMENT_TAGS.startDate)),
    endDate: parseDateTag(tag(TOURNAMENT_TAGS.endDate)),
    place: text(TOURNAMENT_TAGS.place),
    nation: text(TOURNAMENT_TAGS.nation),
    type: text(TOURNAMENT_TAGS.type),
    timeControl: text(TOURNAMENT_TAGS.timeControl),
    rounds: number(TOURNAMENT_TAGS.rounds),
    category: number(TOURNAMENT_TAGS.category),
    complete: text(TOURNAMENT_TAGS.complete) === '1' || undefined,
    teamTournament: text(TOURNAMENT_TAGS.teamTournament) === '1' || undefined,
  };
}

/** A tournament created now, which starts today unless told otherwise. */
export function newTournament(title: string): TournamentInfo {
  const now = new Date();
  return {
    id: null,
    title,
    startDate: { year: now.getFullYear(), month: now.getMonth() + 1, day: now.getDate() },
  };
}

/** What tells tournaments with the same title apart: "2018 · London". */
export function tournamentSubtitle(t: TournamentInfo): string {
  return [t.startDate?.year || undefined, t.place].filter(Boolean).join(' · ');
}

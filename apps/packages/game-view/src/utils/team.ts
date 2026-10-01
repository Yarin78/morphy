import { tagReader } from './tags';

/**
 * A player's team, as edited in the Edit Game Info dialog. With an id, it's an existing team in the
 * database, shared with every other game of it; without one, it's a new team, created (or matched to
 * an identical one) when the game is saved. A player needn't have a team.
 */
export interface TeamInfo {
  id: number | null;
  title: string;
  number?: number;
  /** Whether the year is a season spanning two years, like 2023/24. */
  season?: boolean;
  year?: number;
  /** An IOC code, e.g. 'NOR'. */
  nation?: string;
  /** The number of games of an existing team. */
  gameCount?: number;
}

/** How the Edit Game Info dialog finds and changes existing teams. */
export interface TeamService {
  /** Teams whose title starts with the text, those with the most games first. */
  search(text: string): Promise<TeamInfo[]>;
  get(id: number): Promise<TeamInfo>;
  /** Changes an existing team, for every game of it, and returns it as saved. */
  update(team: TeamInfo): Promise<TeamInfo>;
}

export type TeamColor = 'white' | 'black';

/**
 * The PGN tags holding a player's team on the Chess instance. WhiteTeam and WhiteTeamCountry are
 * PGN tags; the others are only understood by whoever saves the game.
 */
export function teamTags(color: TeamColor) {
  const c = color === 'white' ? 'White' : 'Black';
  return {
    id: `${c}TeamId`,
    title: `${c}Team`,
    number: `${c}TeamNumber`,
    season: `${c}TeamSeason`,
    year: `${c}TeamYear`,
    nation: `${c}TeamCountry`,
  };
}

/** The tags for a player's team: all of teamTags, '' for those not set. */
export function teamToTags(color: TeamColor, t: TeamInfo | null): Record<string, string> {
  const tags = teamTags(color);
  return {
    [tags.id]: t?.id != null ? String(t.id) : '',
    [tags.title]: t?.title ?? '',
    [tags.number]: t?.number ? String(t.number) : '',
    [tags.season]: t?.season ? '1' : '',
    [tags.year]: t?.year ? String(t.year) : '',
    [tags.nation]: t?.nation ?? '',
  };
}

/** Reads a player's team from its tags; null if the player has none. */
export function teamFromTags(color: TeamColor, tag: (name: string) => string): TeamInfo | null {
  const tags = teamTags(color);
  const { text, number, flag, id: idOf } = tagReader(tag);
  const id = idOf(tags.id);
  const title = text(tags.title) ?? '';
  if (id === undefined && !title) return null;
  return {
    id: id ?? null,
    title,
    number: number(tags.number),
    season: flag(tags.season),
    year: number(tags.year),
    nation: text(tags.nation),
  };
}

/** What tells teams with the same title apart: "2024 · NOR". */
export function teamSubtitle(t: TeamInfo): string {
  const year = t.year ? (t.season ? `${t.year}/${String((t.year + 1) % 100).padStart(2, '0')}` : String(t.year)) : undefined;
  return [year, t.nation].filter(Boolean).join(' · ');
}

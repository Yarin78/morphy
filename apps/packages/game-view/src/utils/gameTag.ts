/**
 * A language a game tag can have a title in, by the IOC code of the nation whose flag stands for it.
 */
export type GameTagLanguage = 'ENG' | 'GER' | 'FRA' | 'ESP' | 'ITA' | 'NED' | 'SLO' | 'POR';

export const GAME_TAG_LANGUAGES: { code: GameTagLanguage; name: string }[] = [
  { code: 'ENG', name: 'English' },
  { code: 'GER', name: 'German' },
  { code: 'FRA', name: 'French' },
  { code: 'ESP', name: 'Spanish' },
  { code: 'ITA', name: 'Italian' },
  { code: 'NED', name: 'Dutch' },
  { code: 'SLO', name: 'Slovenian' },
  { code: 'POR', name: 'Portuguese' },
];

/** The languages a database's game tags can have titles in: ChessBase v1 has Slovenian, v2 Portuguese. */
export function gameTagLanguages(format: 'cbh' | '2cbh' | undefined): GameTagLanguage[] {
  const left = format === 'cbh' ? 'POR' : format === '2cbh' ? 'SLO' : undefined;
  return GAME_TAG_LANGUAGES.map((l) => l.code).filter((code) => code !== left);
}

export function gameTagLanguageName(code: GameTagLanguage): string {
  return GAME_TAG_LANGUAGES.find((l) => l.code === code)?.name ?? code;
}

/**
 * A game's tag, as edited in the Edit Game Info dialog: a title in one or more languages. With an
 * id, it's an existing tag in the database, shared with every other game of it; without one, it's a
 * new tag, created (or matched to an identical one) when the game is saved.
 */
export interface GameTagInfo {
  id: number | null;
  /** The titles that are set. */
  titles: Partial<Record<GameTagLanguage, string>>;
  /** The number of games of an existing tag. */
  gameCount?: number;
}

/** How the Edit Game Info dialog finds and changes existing game tags. */
export interface GameTagService {
  /** The languages the database's game tags can have titles in. */
  languages: GameTagLanguage[];
  /** Game tags whose title starts with the text, those with the most games first. */
  search(text: string): Promise<GameTagInfo[]>;
  get(id: number): Promise<GameTagInfo>;
  /** Changes an existing game tag, for every game of it, and returns it as saved. */
  update(gameTag: GameTagInfo): Promise<GameTagInfo>;
}

/** The languages a game tag has titles in. */
export function gameTagTitleLanguages(t: GameTagInfo): GameTagLanguage[] {
  return GAME_TAG_LANGUAGES.map((l) => l.code).filter((code) => t.titles[code]);
}

/** The language of the title a game tag is shown by: English if it has one, else the first it has. */
export function gameTagLanguage(t: GameTagInfo): GameTagLanguage | undefined {
  return t.titles.ENG ? 'ENG' : gameTagTitleLanguages(t)[0];
}

/** The title a game tag is shown by; see gameTagLanguage. */
export function gameTagTitle(t: GameTagInfo): string {
  const language = gameTagLanguage(t);
  return language ? (t.titles[language] ?? '') : '';
}

/**
 * The PGN tags holding the game tag on the Chess instance, only understood by whoever saves the
 * game: its id, and its titles by language.
 */
export const GAME_TAG_TAGS = {
  id: 'GameTagId',
  ...Object.fromEntries(GAME_TAG_LANGUAGES.map((l) => [l.code, `GameTag${l.code}`])),
} as Record<'id' | GameTagLanguage, string>;

/** The tags for a game tag: all of GAME_TAG_TAGS, '' for those not set. */
export function gameTagToTags(t: GameTagInfo | null): Record<string, string> {
  const tags: Record<string, string> = {
    [GAME_TAG_TAGS.id]: t?.id != null ? String(t.id) : '',
  };
  for (const { code } of GAME_TAG_LANGUAGES) {
    tags[GAME_TAG_TAGS[code]] = t?.titles[code] ?? '';
  }
  return tags;
}

/** Reads the game tag from its tags; null if the game has none. */
export function gameTagFromTags(tag: (name: string) => string): GameTagInfo | null {
  const text = (name: string) => {
    const value = tag(name).trim();
    return value && !/^\?+$/.test(value) ? value : undefined;
  };
  const titles: GameTagInfo['titles'] = {};
  for (const { code } of GAME_TAG_LANGUAGES) {
    const title = text(GAME_TAG_TAGS[code]);
    if (title) titles[code] = title;
  }
  const id = text(GAME_TAG_TAGS.id);
  if (id === undefined && Object.keys(titles).length === 0) return null;
  return { id: id !== undefined && /^\d+$/.test(id) ? +id : null, titles };
}

import type { GameTree } from './GameTree';

/**
 * The languages of a game's comments. A comment is either in a language, given by its IOC code
 * like 'ENG', or in none, which ChessBase calls "any language" and which is always shown.
 */

/** The languages ChessBase writes comments in, in the order they're preferred. */
// TODO: Make the order a user setting
const LANGUAGES: readonly { code: string; name: string }[] = [
  { code: 'ENG', name: 'English' },
  { code: 'GER', name: 'German' },
  { code: 'FRA', name: 'French' },
  { code: 'ESP', name: 'Spanish' },
  { code: 'ITA', name: 'Italian' },
  { code: 'NED', name: 'Dutch' },
  { code: 'POR', name: 'Portuguese' },
  { code: 'POL', name: 'Polish' },
  { code: 'GRE', name: 'Greek' },
];

/** The name of a language, or its code if it isn't known. */
export function languageName(code: string): string {
  return LANGUAGES.find((l) => l.code === code)?.name ?? code;
}

function rank(code: string): number {
  const index = LANGUAGES.findIndex((l) => l.code === code);
  return index < 0 ? LANGUAGES.length : index;
}

/** The languages the comments of a game are in, in the order they're preferred. */
export function commentLanguages(game: GameTree): string[] {
  const codes = new Set<string>();
  for (const node of [game.root, ...game.movesInOrder()]) {
    for (const annotation of node.annotations) {
      if ((annotation.type === 'textBefore' || annotation.type === 'textAfter') && annotation.language) {
        codes.add(annotation.language);
      }
    }
  }
  return [...codes].sort((a, b) => rank(a) - rank(b) || a.localeCompare(b));
}

/** The languages shown when a game is opened: the preferred one of those it has, if any. */
export function defaultLanguages(languages: readonly string[]): string[] {
  return languages.length > 0 ? [languages[0]] : [];
}

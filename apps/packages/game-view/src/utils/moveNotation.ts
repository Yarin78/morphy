/**
 * How the pieces of moves are written: as figurines, or by the letters of a language (Sf3 in
 * German for Nf3). The moves are always kept in English SAN; this is only how they're shown.
 */
export type MoveNotation =
  | 'figurine'
  | 'en'
  | 'cs'
  | 'da'
  | 'de'
  | 'es'
  | 'fr'
  | 'hu'
  | 'it'
  | 'nl'
  | 'no'
  | 'pl'
  | 'pt'
  | 'ru'
  | 'sv';

// The king, queen, rook, bishop and knight, in the order of SAN_PIECES
const SAN_PIECES = 'KQRBN';
const PIECES: Record<MoveNotation, readonly string[]> = {
  figurine: ['♔', '♕', '♖', '♗', '♘'],
  en: ['K', 'Q', 'R', 'B', 'N'],
  cs: ['K', 'D', 'V', 'S', 'J'],
  da: ['K', 'D', 'T', 'L', 'S'],
  de: ['K', 'D', 'T', 'L', 'S'],
  es: ['R', 'D', 'T', 'A', 'C'],
  fr: ['R', 'D', 'T', 'F', 'C'],
  hu: ['K', 'V', 'B', 'F', 'H'],
  it: ['R', 'D', 'T', 'A', 'C'],
  nl: ['K', 'D', 'T', 'L', 'P'],
  no: ['K', 'D', 'T', 'L', 'S'],
  pl: ['K', 'H', 'W', 'G', 'S'],
  pt: ['R', 'D', 'T', 'B', 'C'],
  ru: ['Кр', 'Ф', 'Л', 'С', 'К'],
  sv: ['K', 'D', 'T', 'L', 'S'],
};

/** The notations to pick from, figurines and English first, then the languages by name. */
export const MOVE_NOTATIONS: readonly { id: MoveNotation; label: string }[] = [
  { id: 'figurine', label: 'Figurines (♘f3)' },
  { id: 'en', label: 'English (Nf3)' },
  { id: 'cs', label: 'Czech (Jf3)' },
  { id: 'da', label: 'Danish (Sf3)' },
  { id: 'nl', label: 'Dutch (Pf3)' },
  { id: 'fr', label: 'French (Cf3)' },
  { id: 'de', label: 'German (Sf3)' },
  { id: 'hu', label: 'Hungarian (Hf3)' },
  { id: 'it', label: 'Italian (Cf3)' },
  { id: 'no', label: 'Norwegian (Sf3)' },
  { id: 'pl', label: 'Polish (Sf3)' },
  { id: 'pt', label: 'Portuguese (Cf3)' },
  { id: 'ru', label: 'Russian (Кf3)' },
  { id: 'es', label: 'Spanish (Cf3)' },
  { id: 'sv', label: 'Swedish (Sf3)' },
];

// The piece moved, at the start, and the piece promoted to, after the =
const SAN_PIECE_PATTERN = /^[KQRBN]|(?<==)[QRBN]/g;
const FIGURINE_PATTERN = /[♔♕♖♗♘]/g;

/** A move in SAN, as written in a notation. */
export function formatSan(san: string, notation: MoveNotation = 'en'): string {
  if (notation === 'en') return san;
  const pieces = PIECES[notation];
  return san.replace(SAN_PIECE_PATTERN, (piece) => pieces[SAN_PIECES.indexOf(piece)]);
}

/**
 * HTML with the figurines of moves each in a span, so it can be given a font that has them; the
 * same as for the figurines of comments.
 */
export function moveFigurinesToHtml(html: string): string {
  return html.replace(FIGURINE_PATTERN, (c) => `<span class="cbfigurine">${c}</span>`);
}

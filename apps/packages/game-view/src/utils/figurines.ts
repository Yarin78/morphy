/**
 * ChessBase writes the pieces in comments as figurines in two ways, both shown as pieces only in
 * its own fonts: in older, cp1252 text as the characters ¢ £ ¤ ¥ ¦ §, and in UTF-8 text as the
 * private-use characters U+E024 to U+E029. They are shown here as the standard Unicode chess
 * pieces, which the usual symbol fonts have. The text itself is left as it is, so it is saved back
 * unchanged.
 *
 * A real £ or § in an older comment is shown as a piece as well, as ChessBase does.
 */
const FIGURINES: Record<string, string> = {
  '¢': '♔', // ¢
  '£': '♕', // £
  '¤': '♘', // ¤
  '¥': '♗', // ¥
  '¦': '♖', // ¦
  '§': '♙', // §
  '': '♔',
  '': '♕',
  '': '♖',
  '': '♗',
  '': '♘',
  '': '♙',
};

const FIGURINE_PATTERN = /[¢-§-]/g;

/** The text with ChessBase figurines as Unicode chess pieces. */
export function figurinesToUnicode(text: string): string {
  return text.replace(FIGURINE_PATTERN, (c) => FIGURINES[c]);
}

/**
 * HTML with ChessBase figurines as Unicode chess pieces, each in a span so it can be given a
 * font that has them. The figurine characters are never part of markup, so this can be applied
 * to escaped text.
 */
export function figurinesToHtml(html: string): string {
  return html.replace(FIGURINE_PATTERN, (c) => `<span class="cbfigurine">${FIGURINES[c]}</span>`);
}

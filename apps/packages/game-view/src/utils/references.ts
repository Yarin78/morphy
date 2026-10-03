import type { AnnotationOf } from '../model/annotations';
import { escapeHtml } from './html';

/**
 * Web links and game quotations, shown after the move: a link to the page, and a short reference
 * to the quoted game that unfolds to its moves.
 */

/** The address of a web link, if it's one that can safely be opened. */
function safeUrl(url: string): URL | null {
  try {
    const parsed = new URL(url);
    return parsed.protocol === 'http:' || parsed.protocol === 'https:' ? parsed : null;
  } catch {
    return null;
  }
}

/** A web link: its text, or the site's host when it has none, linking to the page. */
export function webLinkHtml(annotation: AnnotationOf<'webLink'>): string {
  const url = safeUrl(annotation.url);
  const text = annotation.text.trim() || (url ? url.host.replace(/^www\./, '') : annotation.url);
  if (!url) {
    return `<span class="cbweblink" title="${escapeHtml(annotation.url)}">${escapeHtml(text)}</span>`;
  }
  return (
    `<a class="cbweblink" href="${escapeHtml(url.href)}" target="_blank" rel="noopener noreferrer" ` +
    `title="${escapeHtml(url.href)}">${escapeHtml(text)}</a>`
  );
}

/** A player's last name, from a name like 'Steinitz, Wilhelm'. */
function lastName(name: string | undefined): string {
  return (name ?? '').split(',')[0].trim();
}

/** The result of a quoted game as shown, or '' if it isn't known or the game isn't over. */
function result(value: string | undefined): string {
  return value === '1/2-1/2' ? '½-½' : value && value !== '*' ? value : '';
}

/**
 * The reference to a quoted game: '1-0 White – Black, Event Year', leaving out what isn't known.
 */
export function quoteReference(header: Readonly<Record<string, string>>): string {
  const names = [lastName(header.white), lastName(header.black)].filter((n) => n).join(' – ');
  const players = [result(header.result), names].filter((p) => p).join(' ');
  const year = /^\d{4}/.exec(header.date ?? '')?.[0];
  const where = [header.event?.trim(), year].filter((p) => p).join(' ');
  return [players, where].filter((p) => p).join(', ') || 'Quoted game';
}

/** A quoted game: a reference to it, which unfolds to its moves when they're quoted too. */
export function quoteHtml(annotation: AnnotationOf<'quote'>): string {
  const reference = escapeHtml(quoteReference(annotation.header));
  const moves = annotation.moves?.trim();
  if (!moves) {
    return `<span class="cbquote" title="Quoted game">${reference}</span>`;
  }
  return (
    `<details class="cbquote"><summary title="Quoted game; click for its moves">${reference}</summary>` +
    `<span class="cbquote-moves">${escapeHtml(moves)}</span></details>`
  );
}

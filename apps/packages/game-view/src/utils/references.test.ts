import { describe, expect, it } from 'vitest';
import { quoteHtml, quoteReference, webLinkHtml } from './references';

describe('web links', () => {
  it('link to the page, showing the site when they have no text', () => {
    const html = webLinkHtml({ type: 'webLink', url: 'http://www.chesshistory.com/winter/extra/bernstein.html', text: '' });
    expect(html).toContain('href="http://www.chesshistory.com/winter/extra/bernstein.html"');
    expect(html).toContain('target="_blank" rel="noopener noreferrer"');
    expect(html).toContain('>chesshistory.com</a>');
  });

  it('show their text when they have one', () => {
    expect(webLinkHtml({ type: 'webLink', url: 'https://example.com/a', text: 'An article' })).toContain('>An article</a>');
  });

  it('are not links when they are not http or https', () => {
    const html = webLinkHtml({ type: 'webLink', url: 'javascript:alert(1)', text: 'x' });
    expect(html).not.toContain('href');
    expect(html).toContain('<span class="cbweblink"');
  });
});

describe('game quotations', () => {
  it('are referred to by the players, event and year', () => {
    expect(
      quoteReference({ white: 'Chigorin, Mikhail', black: 'Steinitz, Wilhelm', date: '1890.??.??', event: 'Cablematch' })
    ).toBe('Chigorin – Steinitz, Cablematch 1890');
    expect(
      quoteReference({ white: 'Anderssen, Adolf', black: 'Dufresne, Jean', result: '1-0', date: '1852.??.??', event: 'Berlin' })
    ).toBe('1-0 Anderssen – Dufresne, Berlin 1852');
    expect(quoteReference({ white: 'A', black: 'B', result: '1/2-1/2' })).toBe('½-½ A – B');
    expect(quoteReference({ white: 'A', black: 'B', result: '*' })).toBe('A – B');
    expect(quoteReference({ white: 'Anderssen, Adolf', date: '????.??.??' })).toBe('Anderssen');
    expect(quoteReference({})).toBe('Quoted game');
  });

  it('unfold to their moves when they have them', () => {
    const header = { white: 'Evans, William Davies', black: 'McDonnell, Alexander' };
    expect(quoteHtml({ type: 'quote', header, moves: '1. e4 e5' })).toContain(
      '<summary title="Quoted game; click for its moves">Evans – McDonnell</summary><span class="cbquote-moves">1. e4 e5</span>'
    );
    expect(quoteHtml({ type: 'quote', header })).toBe('<span class="cbquote" title="Quoted game">Evans – McDonnell</span>');
  });
});

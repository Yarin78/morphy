import { describe, expect, it } from 'vitest';
import type { ChessGame } from 'game-view';
import { previewGame } from './previewGame';

function game(moves: ChessGame['moves']): ChessGame {
  return { header: { id: '1', white: 'A', black: 'B', result: '*', date: '' }, tags: [['White', 'A']], moves };
}

const ANNOTATED = game({
  pgn: '1. e4 (1. d4 d5 (1... Nf6 2. c4)) 1... e5 (1... c5 2. Nf3) 2. Nf3',
  annotations: [
    { move: -1, type: 'textAfter', text: 'Intro' },
    { move: 0, type: 'symbols', nags: [1] },
    { move: 1, type: 'textAfter', text: 'In a variation' },
    { move: 8, type: 'arrows', arrows: [{ from: 'g1', to: 'f3', color: 'green' }] },
  ],
});

describe('previewGame', () => {
  it('shows only the main line', () => {
    const shown = previewGame(ANNOTATED, { variations: false, commentary: false });
    expect(shown.moves?.pgn).toBe('1. e4 e5 2. Nf3');
    expect(shown.moves?.annotations).toEqual([]);
  });

  it('shows the variations without commentary', () => {
    const shown = previewGame(ANNOTATED, { variations: true, commentary: false });
    expect(shown.moves?.pgn).toBe('1. e4 (1. d4 d5 (1... Nf6 2. c4)) 1... e5 (1... c5 2. Nf3) 2. Nf3');
    expect(shown.moves?.annotations).toEqual([]);
  });

  it('shows the commentary of the main line without the variations', () => {
    const shown = previewGame(ANNOTATED, { variations: false, commentary: true });
    expect(shown.moves?.pgn).toBe('1. e4 e5 2. Nf3');
    expect(shown.moves?.annotations).toEqual([
      { move: -1, type: 'textAfter', text: 'Intro' },
      { move: 0, type: 'symbols', nags: [1] },
      { move: 2, type: 'arrows', arrows: [{ from: 'g1', to: 'f3', color: 'green' }] },
    ]);
  });

  it('shows everything when both are on', () => {
    expect(previewGame(ANNOTATED, { variations: true, commentary: true })).toBe(ANNOTATED);
  });

  it('keeps the tags and the start position', () => {
    const fen = '4k3/8/8/8/8/8/4P3/4K3 w - - 0 1';
    const shown = previewGame(game({ pgn: '1. e4 (1. e3) 1... Kd7', fen }), { variations: false, commentary: false });
    expect(shown.tags).toEqual([['White', 'A']]);
    expect(shown.moves?.fen).toBe(fen);
    expect(shown.moves?.pgn).toBe('1. e4 Kd7');
  });

  it('leaves a game it can not read as it is', () => {
    const broken = game({ pgn: '1. e5' });
    expect(previewGame(broken, { variations: false, commentary: false })).toBe(broken);
  });
});

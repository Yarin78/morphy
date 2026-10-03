import { describe, expect, it } from 'vitest';
import { GameTree } from '../model/GameTree';
import { lineStart, nextMoveChoices } from './variationChoice';

describe('choosing the next move', () => {
  const tree = GameTree.fromMoves({ pgn: '1.e4 e5 (1...c5 2.Nf3 d6 3.d4 cxd4) (1...e6 2.d4) 2.Nf3' });
  const e4 = tree.root.children[0];

  it('lists the variations first and the main move last', () => {
    expect(nextMoveChoices(e4).map((m) => m.san)).toEqual(['c5', 'e6', 'e5']);
    expect(nextMoveChoices(e4.children[0].children[0])).toEqual([]);
  });

  it('shows the start of the line of each move', () => {
    const [c5, e6, e5] = nextMoveChoices(e4);
    expect(lineStart(c5)).toBe('1...c5 2.Nf3 d6 3.d4');
    expect(lineStart(e6)).toBe('1...e6 2.d4');
    expect(lineStart(e5)).toBe('1...e5 2.Nf3');
    expect(lineStart(e4)).toBe('1.e4 e5 2.Nf3');
  });

  it('shows the symbols of good and bad moves, but not the others', () => {
    const annotated = GameTree.fromMoves({
      pgn: '1.e4 e5 2.Nf3',
      annotations: [
        { move: 0, type: 'symbols', nags: [3, 14] },
        { move: 1, type: 'symbols', nags: [6] },
      ],
    });
    expect(lineStart(annotated.root.children[0])).toBe('1.e4!! e5?! 2.Nf3');
  });
});

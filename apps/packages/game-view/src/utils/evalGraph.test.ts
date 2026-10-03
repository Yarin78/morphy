import { describe, expect, it } from 'vitest';
import { GameTree } from '../model/GameTree';
import { evalBars, evalValue } from './evalGraph';

describe('the evaluation graph', () => {
  it('has a bar for each evaluated position of the main line, with its move', () => {
    const tree = GameTree.fromMoves({ pgn: '1.e4 e5 (1...c5) 2.Nf3' });
    const bars = evalBars(tree.root, {
      type: 'evaluations',
      evaluations: [
        { eval: 17, depth: 1, evalType: 0 },
        { eval: -30, depth: 20, evalType: 0 },
        { eval: 0, depth: 0, evalType: 255 },
      ],
    });
    expect(bars.map((b) => b.label)).toEqual(['Start +0.17/1', '1.e4 -0.30/20', '1...e5']);
    expect(bars[0].node).toBe(tree.root);
    expect(bars[1].value).toBeLessThan(0);
    expect(bars[2].value).toBeNull();
  });

  it('stops at the end of the main line', () => {
    const tree = GameTree.fromMoves({ pgn: '1.e4' });
    const evaluation = { eval: 0, depth: 1, evalType: 0 };
    expect(evalBars(tree.root, { type: 'evaluations', evaluations: [evaluation, evaluation, evaluation] })).toHaveLength(2);
  });

  it('grows with the evaluation, to the full height for a mate', () => {
    expect(evalValue({ eval: 0, evalType: 0 })).toBe(0);
    expect(evalValue({ eval: 100, evalType: 0 })).toBeCloseTo(0.45, 2);
    expect(evalValue({ eval: -900, evalType: 0 })).toBe(-1);
    expect(evalValue({ eval: 3, evalType: 1 })).toBe(1);
    expect(evalValue({ eval: -3, evalType: 1 })).toBe(-1);
    expect(evalValue({ eval: 5, evalType: 32 })).toBeNull();
  });
});

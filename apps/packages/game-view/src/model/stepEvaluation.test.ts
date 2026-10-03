import { describe, expect, it } from 'vitest';
import type { Annotation } from './annotations';
import { nagsOf, stepEvaluation } from './nags';

const evaluate = (nags: number[], ...steps: (1 | -1)[]) => {
  let annotations: Annotation[] = nags.length > 0 ? [{ type: 'symbols', nags }] : [];
  for (const step of steps) annotations = stepEvaluation(annotations, step);
  return [...nagsOf(annotations)];
};

describe('stepping the evaluation of the position', () => {
  it('goes a step better for White or Black, from equal when there is none', () => {
    expect(evaluate([], 1)).toEqual([14]);
    expect(evaluate([], -1)).toEqual([15]);
    expect(evaluate([], 1, 1, 1)).toEqual([18]);
    expect(evaluate([14], -1)).toEqual([10]);
  });

  it('stops at the ends', () => {
    expect(evaluate([18], 1)).toEqual([18]);
    expect(evaluate([19], -1)).toEqual([19]);
  });

  it('counts an evaluation off the scale as equal, and keeps the symbol of the move', () => {
    expect(evaluate([1, 13], 1)).toEqual([1, 14]);
    expect(evaluate([11], -1)).toEqual([15]);
  });
});

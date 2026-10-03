import { describe, expect, it } from 'vitest';
import type { Annotation } from './annotations';
import { nagsOf, typeMoveComment } from './nags';

/** Types keys one after the other, from annotations, as the keyboard does. */
function type(keys: string, annotations: Annotation[] = []): number[] {
  let typed: string | null = null;
  for (const key of keys) {
    ({ annotations, typed } = typeMoveComment(annotations, key as '!' | '?', typed));
  }
  return [...nagsOf(annotations)];
}

describe('typing the symbol of a good or bad move', () => {
  it('gives one key its symbol', () => {
    expect(type('!')).toEqual([1]);
    expect(type('?')).toEqual([2]);
  });

  it('makes two keys one symbol', () => {
    expect(type('!!')).toEqual([3]);
    expect(type('??')).toEqual([4]);
    expect(type('!?')).toEqual([5]);
    expect(type('?!')).toEqual([6]);
  });

  it('takes the symbol away on a third key', () => {
    expect(type('!!!')).toEqual([]);
    expect(type('!?!')).toEqual([]);
  });

  it('takes the symbol away when a key on its own is the one the move has', () => {
    const good: Annotation[] = [{ type: 'symbols', nags: [1, 14] }];
    expect(type('!', good)).toEqual([14]);
    // Not typed on: a key on a move that has a symbol from before replaces it
    expect(type('?', good)).toEqual([2, 14]);
  });

  it('keeps the evaluation of the position', () => {
    expect(type('!?', [{ type: 'symbols', nags: [14] }])).toEqual([5, 14]);
  });

  it("starts over when the move's symbol isn't what was typed", () => {
    const { annotations } = typeMoveComment([{ type: 'symbols', nags: [4] }], '!', '!');
    expect(nagsOf(annotations)).toEqual([1]);
  });
});

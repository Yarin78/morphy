import { describe, expect, it } from 'vitest';
import type { Annotation } from './annotations';
import { isPrefixNag, nagInfo, NAG_PALETTE, nagsOf, toggleNag } from './nags';

describe('NAGs', () => {
  it('are added and removed again', () => {
    const added = toggleNag([], 1);
    expect(added).toEqual([{ type: 'symbols', nags: [1] }]);
    expect(toggleNag(added, 1)).toEqual([]);
  });

  it('replace the one of the same type, and keep the others', () => {
    let annotations: Annotation[] = [{ type: 'textAfter', text: 'x' }];
    annotations = toggleNag(annotations, 142); // prefix
    annotations = toggleNag(annotations, 1); // move comment
    annotations = toggleNag(annotations, 16); // evaluation
    expect(nagsOf(annotations)).toEqual([1, 16, 142]);
    annotations = toggleNag(annotations, 5);
    annotations = toggleNag(annotations, 146);
    annotations = toggleNag(annotations, 143);
    expect(nagsOf(annotations)).toEqual([5, 146, 143]);
    expect(annotations[0]).toEqual({ type: 'textAfter', text: 'x' });
  });

  it('keep NAGs of no known type when others are added', () => {
    expect(nagsOf(toggleNag([{ type: 'symbols', nags: [77] }], 1))).toEqual([1, 77]);
  });

  it('offer only NAGs with a symbol, grouped by their type', () => {
    for (const group of NAG_PALETTE) {
      for (const nag of group.nags) {
        expect(nagInfo(nag)?.type).toBe(group.type);
      }
    }
    expect(isPrefixNag(142)).toBe(true);
    expect(isPrefixNag(146)).toBe(false);
  });
});

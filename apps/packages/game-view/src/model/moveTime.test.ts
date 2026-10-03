import { describe, expect, it } from 'vitest';
import type { Annotation } from './annotations';
import { parseTime, withClocks, withTimeSpent } from './moveTime';

describe('the time of a move', () => {
  it('is typed as h:mm:ss, m:ss or seconds', () => {
    expect(parseTime('1:02:03')).toBe(3723);
    expect(parseTime(' 5:07 ')).toBe(307);
    expect(parseTime('90')).toBe(90);
    expect(parseTime('')).toBeUndefined();
    expect(parseTime('5:75')).toBeNull();
    expect(parseTime('1:60:00')).toBeNull();
    expect(parseTime('abc')).toBeNull();
  });

  const text: Annotation = { type: 'textAfter', text: 'Fast' };

  it('is the clocks, taking the time spent away', () => {
    const spent: Annotation = { type: 'timeSpent', hours: 0, minutes: 1, seconds: 2 };
    expect(withClocks([text, spent], 360000, undefined)).toEqual([text, { type: 'whiteClock', centiseconds: 360000 }]);
  });

  it('or the time spent, taking the clocks away and keeping what ChessBase keeps with it', () => {
    const clock: Annotation = { type: 'blackClock', centiseconds: 100 };
    const spent: Annotation = { type: 'timeSpent', hours: 0, minutes: 0, seconds: 1, unknown: 7 };
    expect(withTimeSpent([text, clock, spent], 3723)).toEqual([
      text,
      { type: 'timeSpent', hours: 1, minutes: 2, seconds: 3, unknown: 7 },
    ]);
    expect(withTimeSpent([text, clock], undefined)).toEqual([text]);
  });
});

import { describe, expect, it } from 'vitest';
import { formatCount, scoreOf } from './positionStats';

describe('formatCount', () => {
  it('shows counts under a thousand in full, larger ones to two digits', () => {
    expect(formatCount(747)).toBe('747');
    expect(formatCount(1659)).toBe('1.7k');
    expect(formatCount(57437)).toBe('57k');
    expect(formatCount(123456)).toBe('120k');
    expect(formatCount(1234567)).toBe('1.2M');
  });
});

describe('scoreOf', () => {
  it('counts a draw as half a point, for either side', () => {
    const results = { games: 10, whiteWins: 5, draws: 2, blackWins: 3 };
    expect(scoreOf(results, true)).toBe(0.6);
    expect(scoreOf(results, false)).toBe(0.4);
  });

  it('leaves out the games without a result', () => {
    expect(scoreOf({ games: 12, whiteWins: 5, draws: 2, blackWins: 3 }, true)).toBe(0.6);
  });
});

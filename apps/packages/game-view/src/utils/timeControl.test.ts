import { describe, expect, it } from 'vitest';
import { formatTimeControl, parseTimeControl, timeControlError, toPgnTimeControl } from './timeControl';

const FIDE_CLASSICAL = [
  { seconds: 5400, increment: 30, moves: 40 },
  { seconds: 1800, increment: 30 },
];

describe('time controls', () => {
  it('shows times in hours, minutes or seconds, increments in seconds', () => {
    expect(formatTimeControl(FIDE_CLASSICAL)).toBe('40/90m+30s:30m+30s');
    expect(formatTimeControl([{ seconds: 9000, increment: 0, moves: 40 }])).toBe('40/2.5h');
    expect(formatTimeControl([{ seconds: 3600, increment: 0 }])).toBe('1h');
    expect(formatTimeControl([{ seconds: 5400, increment: 0 }])).toBe('90m');
    expect(formatTimeControl([{ seconds: 135, increment: 0 }])).toBe('135s');
    expect(formatTimeControl([{ seconds: 180, increment: 2 }])).toBe('3m+2s');
    expect(formatTimeControl(undefined)).toBe('');
  });

  it('writes the PGN tag in seconds', () => {
    expect(toPgnTimeControl(FIDE_CLASSICAL)).toBe('40/5400+30:1800+30');
  });

  it('reads what it shows, and the PGN tag', () => {
    expect(parseTimeControl('40/90m+30s:30m+30s')).toEqual(FIDE_CLASSICAL);
    expect(parseTimeControl('40/5400+30:1800+30')).toEqual(FIDE_CLASSICAL);
    expect(parseTimeControl('1h30m')).toEqual([{ seconds: 5400, increment: 0 }]);
    expect(parseTimeControl('2.5h')).toEqual([{ seconds: 9000, increment: 0 }]);
  });

  it('takes no time control as none', () => {
    expect(parseTimeControl('')).toBeUndefined();
    expect(parseTimeControl('?')).toBeUndefined();
    expect(parseTimeControl('-')).toBeUndefined();
  });

  it('turns down what is not a time control', () => {
    // Every period but the last needs a number of moves
    expect(parseTimeControl('5m:3m')).toBeNull();
    expect(parseTimeControl('40/90m:')).toBeNull();
    expect(parseTimeControl('0')).toBeNull();
    expect(parseTimeControl('90.5s')).toBeNull();
    expect(parseTimeControl('*180')).toBeNull();
    expect(timeControlError('abc')).toBeDefined();
    expect(timeControlError('90m+30s')).toBeUndefined();
  });
});

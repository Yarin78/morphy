import { describe, expect, it } from 'vitest';
import { formatClock, formatEval, isMoveInfo, moveInfoHtml } from './moveInfo';

describe('move info', () => {
  it('shows clock times to the second, with hours only when there are any', () => {
    expect(formatClock(543210)).toBe('1:30:32');
    expect(formatClock(900000)).toBe('2:30:00');
    expect(formatClock(6099)).toBe('1:00');
    expect(formatClock(0)).toBe('0:00');
  });

  it('shows evaluations in pawns or as mates, with the depth when it is known', () => {
    expect(formatEval({ type: 'eval', eval: 53, evalType: 0, depth: 22 })).toBe('+0.53/22');
    expect(formatEval({ type: 'eval', eval: -120, evalType: 0, depth: 0 })).toBe('-1.20');
    expect(formatEval({ type: 'eval', eval: 0, evalType: 0, depth: 0 })).toBe('+0.00');
    expect(formatEval({ type: 'eval', eval: 3, evalType: 1, depth: 18 })).toBe('#3/18');
    expect(formatEval({ type: 'eval', eval: -2, evalType: 1, depth: 0 })).toBe('#-2');
    expect(formatEval({ type: 'eval', eval: 0, evalType: 1, depth: 0 })).toBe('#');
    expect(formatEval({ type: 'eval', eval: 7, evalType: 3, depth: 0 })).toBeNull();
  });

  it('leave evaluations of an unknown kind to a marker', () => {
    expect(isMoveInfo({ type: 'eval', eval: 7, evalType: 3, depth: 0 })).toBe(false);
    expect(isMoveInfo({ type: 'timeSpent', hours: 0, minutes: 0, seconds: 5 })).toBe(true);
  });

  it('is shown after the move, with the details in the tooltip', () => {
    expect(
      moveInfoHtml([
        { type: 'whiteClock', centiseconds: 543210 },
        { type: 'timeSpent', hours: 0, minutes: 0, seconds: 5 },
        { type: 'textAfter', text: 'ignored' },
      ])
    ).toBe(
      '<span class="cbmoveinfo cbclock" title="White\'s clock: 1:30:32">◷1:30:32</span>' +
        '<span class="cbmoveinfo cbtimespent" title="Time spent: 0:05">0:05</span>'
    );
    expect(moveInfoHtml([{ type: 'blackClock', centiseconds: 900000 }], true)).toContain(
      "Black's clock at the start: 2:30:00"
    );
  });
});

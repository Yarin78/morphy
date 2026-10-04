import { describe, expect, it } from 'vitest';
import { formatScore, lineToSan, scoreForWhite } from './lines';
import { parseInfo, parseOption } from './uci';

const START = 'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1';
const AFTER_E4 = 'rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1';

describe('engine lines', () => {
  it('reads an info line of a search', () => {
    expect(
      parseInfo('info depth 22 seldepth 31 multipv 2 score cp -35 lowerbound nodes 123456 nps 987654 time 125 pv e7e5 g1f3')
    ).toEqual({
      depth: 22,
      seldepth: 31,
      multipv: 2,
      score: { kind: 'cp', value: -35 },
      bound: 'lower',
      nodes: 123456,
      nps: 987654,
      time: 125,
      pv: ['e7e5', 'g1f3'],
    });
    expect(parseInfo('info depth 5 score mate -3 pv a1a2')).toEqual({
      depth: 5,
      multipv: 1,
      score: { kind: 'mate', value: -3 },
      pv: ['a1a2'],
    });
    expect(parseInfo('info string NNUE evaluation using nn-1111.nnue')).toBeNull();
    expect(parseInfo('bestmove e2e4')).toBeNull();
  });

  it('reads the options of the handshake', () => {
    expect(parseOption('option name Threads type spin default 1 min 1 max 1024')).toEqual({
      name: 'Threads',
      type: 'spin',
      default: '1',
      min: 1,
      max: 1024,
    });
    expect(parseOption('option name Clear Hash type button')).toEqual({ name: 'Clear Hash', type: 'button' });
  });

  it('shows scores from White’s point of view', () => {
    expect(formatScore(scoreForWhite({ kind: 'cp', value: 34 }, true))).toBe('+0.34');
    expect(formatScore(scoreForWhite({ kind: 'cp', value: 120 }, false))).toBe('-1.20');
    expect(formatScore({ kind: 'cp', value: 0 })).toBe('0.00');
    expect(formatScore(scoreForWhite({ kind: 'mate', value: 3 }, false))).toBe('#-3');
  });

  it('writes a line as SAN with move numbers', () => {
    expect(lineToSan(START, ['e2e4', 'e7e5', 'g1f3'])).toBe('1.e4 e5 2.Nf3');
    expect(lineToSan(AFTER_E4, ['e7e5', 'g1f3', 'b8c6'])).toBe('1...e5 2.Nf3 Nc6');
    expect(lineToSan(START, ['e2e4', 'e7e5', 'g1f3'], 2)).toBe('1.e4 e5');
    // An impossible move ends it
    expect(lineToSan(START, ['e2e4', 'e2e4'])).toBe('1.e4');
  });
});

import { describe, expect, it } from 'vitest';
import { formatSan, moveFigurinesToHtml } from './moveNotation';

describe('formatSan', () => {
  it('leaves English as it is', () => {
    expect(formatSan('Nxe5+')).toBe('Nxe5+');
  });

  it('writes the piece moved and the one promoted to in the language', () => {
    expect(formatSan('Nf3', 'de')).toBe('Sf3');
    expect(formatSan('Qxd8#', 'fr')).toBe('Dxd8#');
    expect(formatSan('exd8=Q+', 'sv')).toBe('exd8=D+');
    expect(formatSan('Kg1', 'ru')).toBe('Крg1');
  });

  it('leaves pawn moves, castling and files alone', () => {
    expect(formatSan('bxc3', 'de')).toBe('bxc3');
    expect(formatSan('O-O-O', 'de')).toBe('O-O-O');
    expect(formatSan('Rbd1', 'de')).toBe('Tbd1');
  });

  it('writes figurines', () => {
    expect(formatSan('Nbd7', 'figurine')).toBe('♘bd7');
    expect(formatSan('a1=N', 'figurine')).toBe('a1=♘');
    expect(moveFigurinesToHtml('12.♘bd7')).toBe('12.<span class="cbfigurine">♘</span>bd7');
  });
});

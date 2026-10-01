import { describe, expect, it } from 'vitest';
import { decodeEloType, eloTypeLabel, encodeEloType, FIDE, sameEloType } from './eloType';
import type { EloTypeInfo } from './eloType';

describe('rating types', () => {
  it('round-trips through its tag', () => {
    const types: EloTypeInfo[] = [
      FIDE,
      { kind: 'NATIONAL', timeControl: 'RAPID', nation: 'NOR' },
      { kind: 'SERVER', timeControl: 'BLITZ', name: 'lichess' },
      { kind: 'INTERNATIONAL', timeControl: 'CORRESPONDENCE', name: 'ICCF' },
    ];
    for (const type of types) {
      expect(decodeEloType(encodeEloType(type))).toEqual(type);
    }
  });

  it('reads nothing from a tag it does not know', () => {
    expect(decodeEloType('')).toBeNull();
    expect(decodeEloType('WORLD/NORMAL//')).toBeNull();
    expect(decodeEloType('SERVER/SLOW//x')).toBeNull();
  });

  it('takes no type as FIDE', () => {
    expect(sameEloType(null, FIDE)).toBe(true);
    expect(sameEloType(null, { kind: 'SERVER', timeControl: 'NORMAL', name: 'lichess' })).toBe(false);
  });

  it('is labelled without the time controls that go without saying', () => {
    expect(eloTypeLabel(null)).toBe('FIDE');
    expect(eloTypeLabel({ kind: 'INTERNATIONAL', timeControl: 'BLITZ', name: 'FIDE' })).toBe('FIDE blitz');
    expect(eloTypeLabel({ kind: 'INTERNATIONAL', timeControl: 'CORRESPONDENCE', name: 'ICCF' })).toBe('ICCF');
    expect(eloTypeLabel({ kind: 'NATIONAL', timeControl: 'RAPID', nation: 'NOR' })).toBe('Norway rapid');
  });
});

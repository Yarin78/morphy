/** What kind of rating an elo is; see se.yarin.chess.EloType on the server. */
export interface EloTypeInfo {
  kind: EloKind;
  timeControl: EloTimeControl;
  /** The IOC code of a national rating's nation. */
  nation?: string;
  /** FIDE or ICCF for an international rating, the server's name for a server one. */
  name?: string;
}

export type EloKind = 'INTERNATIONAL' | 'NATIONAL' | 'SERVER';
export type EloTimeControl = 'NORMAL' | 'BULLET' | 'BLITZ' | 'RAPID' | 'CORRESPONDENCE';

export const ELO_KINDS: { value: EloKind; label: string }[] = [
  { value: 'INTERNATIONAL', label: 'International' },
  { value: 'NATIONAL', label: 'National' },
  { value: 'SERVER', label: 'Server' },
];

export const ELO_TIME_CONTROLS: { value: EloTimeControl; label: string }[] = [
  { value: 'NORMAL', label: 'Normal' },
  { value: 'BULLET', label: 'Bullet' },
  { value: 'BLITZ', label: 'Blitz' },
  { value: 'RAPID', label: 'Rapid' },
  { value: 'CORRESPONDENCE', label: 'Correspondence' },
];

/** The chess servers a rating can be from, by the names the server side has for them. */
export const ELO_SERVERS = ['ChessBase', 'chess.com', 'lichess'];

/** An international rating: ICCF for correspondence, otherwise FIDE. */
export function internationalEloType(timeControl: EloTimeControl): EloTypeInfo {
  return { kind: 'INTERNATIONAL', timeControl, name: timeControl === 'CORRESPONDENCE' ? 'ICCF' : 'FIDE' };
}

export const FIDE: EloTypeInfo = internationalEloType('NORMAL');

/** A short label: "FIDE", "FIDE blitz", "ICCF", "NOR rapid", "lichess blitz". */
export function eloTypeLabel(type: EloTypeInfo | null): string {
  const t = type ?? FIDE;
  const what = t.kind === 'NATIONAL' ? t.nation || 'National' : t.name || (t.kind === 'SERVER' ? 'Server' : 'FIDE');
  // Normal is the default, and ICCF is correspondence by definition
  const showTimeControl = t.timeControl !== 'NORMAL' && !(t.kind === 'INTERNATIONAL' && t.timeControl === 'CORRESPONDENCE');
  return showTimeControl ? `${what} ${t.timeControl.toLowerCase()}` : what;
}

export function sameEloType(a: EloTypeInfo | null, b: EloTypeInfo | null): boolean {
  return encodeEloType(a ?? FIDE) === encodeEloType(b ?? FIDE);
}

/** Not standard PGN tags: the types of the elos, understood only by whoever saves the game. */
export const ELO_TYPE_TAGS = { white: 'WhiteEloType', black: 'BlackEloType' } as const;

/** The tag form, "kind/timeControl/nation/name", as the server's EloType.encode. */
export function encodeEloType(type: EloTypeInfo | null): string {
  return type ? `${type.kind}/${type.timeControl}/${type.nation ?? ''}/${type.name ?? ''}` : '';
}

export function decodeEloType(text: string): EloTypeInfo | null {
  const [kind, timeControl, nation, name] = text.split('/');
  if (!ELO_KINDS.some((k) => k.value === kind) || !ELO_TIME_CONTROLS.some((t) => t.value === timeControl)) {
    return null;
  }
  return {
    kind: kind as EloKind,
    timeControl: timeControl as EloTimeControl,
    ...(nation ? { nation } : {}),
    ...(name ? { name } : {}),
  };
}

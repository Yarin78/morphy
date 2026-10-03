/** One period of a time control, played after the one before. */
export interface TimeControlPeriod {
  /** The time the period starts with, in seconds. */
  seconds: number;
  /** Added after each move, in seconds. */
  increment: number;
  /** The number of moves of the period; none for the rest of the game. */
  moves?: number;
}

/** The PGN tag with the time control, in seconds; see toPgnTimeControl. */
export const TIME_CONTROL_TAG = 'TimeControl';

/** A time in the shortest way: 2h, 2.5h (from two hours), 90m, or else in seconds. */
function formatDuration(seconds: number): string {
  if (seconds > 0 && seconds % 3600 === 0) return `${seconds / 3600}h`;
  if (seconds >= 7200 && seconds % 1800 === 0) return `${seconds / 3600}h`;
  if (seconds > 0 && seconds % 60 === 0) return `${seconds / 60}m`;
  return `${seconds}s`;
}

/**
 * A time control as it's typed and shown: like the PGN TimeControl tag, the periods separated by
 * ":", each the number of moves and a "/" if it has one, the time, and "+" and the increment if it
 * has one, but with the time in hours, minutes or seconds: "40/90m+30s:30m+30s", "40/2.5h", "5m+3s".
 */
export function formatTimeControl(periods: TimeControlPeriod[] | undefined): string {
  return (periods ?? [])
    .map(
      (p) => `${p.moves ? `${p.moves}/` : ''}${formatDuration(p.seconds)}${p.increment ? `+${p.increment}s` : ''}`
    )
    .join(':');
}

/** A time in words: 2 h, 2.5 h (from two hours), 90 min, 1 h 45 min (over 90 minutes), or 30 s. */
function durationInWords(seconds: number): string {
  if (seconds > 0 && seconds % 3600 === 0) return `${seconds / 3600} h`;
  if (seconds >= 7200 && seconds % 1800 === 0) return `${seconds / 3600} h`;
  if (seconds > 0 && seconds % 60 === 0) {
    const minutes = seconds / 60;
    return minutes > 90 ? `${Math.floor(minutes / 60)} h ${minutes % 60} min` : `${minutes} min`;
  }
  return `${seconds} s`;
}

/**
 * A time control in words, a line for each period: "40 moves in 90 min, +30 s per move", then
 * "30 min for the rest of the game, +30 s per move".
 */
export function describeTimeControl(periods: TimeControlPeriod[] | undefined): string[] {
  return (periods ?? []).map((p, i) => {
    const time = durationInWords(p.seconds);
    const period = p.moves
      ? `${p.moves} move${p.moves === 1 ? '' : 's'} in ${time}`
      : `${time} for the ${i === 0 ? 'game' : 'rest of the game'}`;
    return p.increment ? `${period}, +${durationInWords(p.increment)} per move` : period;
  });
}

/** The time control as a PGN TimeControl tag, with every time in seconds: "40/5400+30:1800+30". */
export function toPgnTimeControl(periods: TimeControlPeriod[] | undefined): string {
  return (periods ?? [])
    .map((p) => `${p.moves ? `${p.moves}/` : ''}${p.seconds}${p.increment ? `+${p.increment}` : ''}`)
    .join(':');
}

/** A time in hours, minutes and seconds, like 2.5h, 90m, 1h30m or 30s; just a number is seconds. */
function parseDuration(text: string): number | null {
  const match = text.trim().match(/^(?:(\d+(?:\.\d+)?)h)?\s*(?:(\d+(?:\.\d+)?)m)?\s*(?:(\d+)s?)?$/i);
  if (!match || !text.trim()) return null;
  const [, hours, minutes, seconds] = match;
  const total = (hours ? +hours * 3600 : 0) + (minutes ? +minutes * 60 : 0) + (seconds ? +seconds : 0);
  return Number.isInteger(total) ? total : null;
}

/**
 * The time control in a field's text, see formatTimeControl; it also reads the PGN tag, where a
 * time is just a number of seconds. Undefined for none ('', or the PGN tag's '?' and '-'), null for
 * text that isn't a time control. Every period but the last needs a number of moves, since the
 * last is for the rest of the game unless it has one too.
 */
export function parseTimeControl(text: string): TimeControlPeriod[] | undefined | null {
  const value = text.trim();
  if (!value || value === '?' || value === '-') return undefined;
  const fields = value.split(':');
  const periods: TimeControlPeriod[] = [];
  for (const [i, field] of fields.entries()) {
    const match = field.trim().match(/^(?:(\d+)\s*\/)?([^+]+)(?:\+(.+))?$/);
    if (!match) return null;
    const moves = match[1] ? parseInt(match[1], 10) : undefined;
    const seconds = parseDuration(match[2]);
    const increment = match[3] === undefined ? 0 : parseDuration(match[3]);
    if (seconds === null || seconds === 0 || increment === null || moves === 0) return null;
    if (moves === undefined && i < fields.length - 1) return null;
    periods.push(moves === undefined ? { seconds, increment } : { seconds, increment, moves });
  }
  return periods;
}

/** What's wrong with a time control field's text, if anything. */
export function timeControlError(text: string): string | undefined {
  return parseTimeControl(text) === null ? 'Like 90m+30s, 40/2h:30m or 5m+3s' : undefined;
}

/** Time controls that are often played, to pick from rather than type. */
export const TIME_CONTROL_PRESETS: { text: string; name: string }[] = [
  { text: '40/90m+30s:30m+30s', name: 'Classical, FIDE' },
  { text: '90m+30s', name: 'Classical' },
  { text: '40/2h:20/1h:30m', name: 'Classical, no increment' },
  { text: '25m+10s', name: 'Rapid' },
  { text: '15m+10s', name: 'Rapid, FIDE' },
  { text: '10m+5s', name: 'Rapid' },
  { text: '10m', name: 'Rapid' },
  { text: '5m+3s', name: 'Blitz' },
  { text: '3m+2s', name: 'Blitz, FIDE' },
  { text: '5m', name: 'Blitz' },
  { text: '3m', name: 'Blitz' },
  { text: '2m+1s', name: 'Bullet' },
  { text: '1m', name: 'Bullet' },
];

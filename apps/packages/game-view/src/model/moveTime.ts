import type { Annotation, AnnotationOf } from './annotations';
import { findAnnotation } from './annotations';

/**
 * The time of a move: either the time left on both clocks after it, or the time spent on it.
 * Setting one takes the other away.
 */

const TIME_TYPES: readonly Annotation['type'][] = ['whiteClock', 'blackClock', 'timeSpent'];

/**
 * A time as it's typed, h:mm:ss, m:ss or just seconds, in seconds; undefined for none (''), null
 * for text that isn't a time.
 */
export function parseTime(text: string): number | undefined | null {
  const value = text.trim();
  if (!value) return undefined;
  const match = value.match(/^(?:(?:(\d+):)?(\d{1,2}):)?(\d{1,2})$/) ?? value.match(/^()()(\d+)$/);
  if (!match) return null;
  const [, hours, minutes, seconds] = match;
  if (minutes && +minutes >= 60 && hours) return null;
  if (match[0].includes(':') && +seconds >= 60) return null;
  return (hours ? +hours * 3600 : 0) + (minutes ? +minutes * 60 : 0) + +seconds;
}

/** The annotations without the time of the move. */
function withoutTime(annotations: readonly Annotation[]): Annotation[] {
  return annotations.filter((a) => !TIME_TYPES.includes(a.type));
}

/**
 * The annotations with the time left on the clocks after the move, in hundredths of a second, or
 * none for a clock not given; the time spent goes.
 */
export function withClocks(
  annotations: readonly Annotation[],
  white: number | undefined,
  black: number | undefined
): Annotation[] {
  const clocks: Annotation[] = [];
  if (white !== undefined) clocks.push({ type: 'whiteClock', centiseconds: white });
  if (black !== undefined) clocks.push({ type: 'blackClock', centiseconds: black });
  return [...withoutTime(annotations), ...clocks];
}

/** The annotations with the time spent on the move, in seconds, or none; the clocks go. */
export function withTimeSpent(annotations: readonly Annotation[], seconds: number | undefined): Annotation[] {
  if (seconds === undefined) return withoutTime(annotations);
  const unknown = findAnnotation(annotations, 'timeSpent')?.unknown;
  const spent: AnnotationOf<'timeSpent'> = {
    type: 'timeSpent',
    hours: Math.floor(seconds / 3600),
    minutes: Math.floor(seconds / 60) % 60,
    seconds: seconds % 60,
    ...(unknown !== undefined ? { unknown } : {}),
  };
  return [...withoutTime(annotations), spent];
}

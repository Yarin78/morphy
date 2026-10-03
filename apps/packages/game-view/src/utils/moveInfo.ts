import type { Annotation, AnnotationOf } from '../model/annotations';

/**
 * Clock times, the time spent on a move and engine evaluations, shown as small text after the
 * move: '◷1:23:45', '0:05' and '+0.53/22' or '#3'.
 */

/** Hours, minutes and seconds as 'h:mm:ss', or 'm:ss' under an hour. */
function formatTime(hours: number, minutes: number, seconds: number): string {
  const ss = String(seconds).padStart(2, '0');
  return hours > 0 ? `${hours}:${String(minutes).padStart(2, '0')}:${ss}` : `${minutes}:${ss}`;
}

/** A clock time, given in hundredths of a second, to the whole second. */
export function formatClock(centiseconds: number): string {
  const total = Math.floor(centiseconds / 100);
  return formatTime(Math.floor(total / 3600), Math.floor(total / 60) % 60, total % 60);
}

/** The time spent on a move. */
export function formatTimeSpent(annotation: AnnotationOf<'timeSpent'>): string {
  return formatTime(annotation.hours, annotation.minutes, annotation.seconds);
}

/**
 * An engine evaluation: in pawns from White's side, like '+0.53', or as a mate, like '#3' or
 * '#-3' for Black mating, with the search depth after a '/' when it's known. Null for a kind of
 * evaluation that isn't known.
 */
export function formatEval(annotation: AnnotationOf<'eval'>): string | null {
  const depth = annotation.depth > 0 ? `/${annotation.depth}` : '';
  switch (annotation.evalType) {
    case 0: {
      const pawns = (annotation.eval / 100).toFixed(2);
      return (annotation.eval >= 0 ? '+' : '') + pawns + depth;
    }
    case 1:
      return (annotation.eval === 0 ? '#' : `#${annotation.eval}`) + depth;
    default:
      return null;
  }
}

/** Whether the notation shows an annotation as move info rather than with a marker. */
export function isMoveInfo(annotation: Annotation): boolean {
  switch (annotation.type) {
    case 'whiteClock':
    case 'blackClock':
    case 'timeSpent':
      return true;
    case 'eval':
      return formatEval(annotation) !== null;
    default:
      return false;
  }
}

function span(className: string, title: string, text: string): string {
  return `<span class="cbmoveinfo ${className}" title="${title}">${text}</span>`;
}

/**
 * The move info of a move as HTML, in the order the annotations are in.
 *
 * @param game whether these are the annotations of the game as a whole, where the clocks are those
 *     at the start
 */
export function moveInfoHtml(annotations: readonly Annotation[], game = false): string {
  const parts: string[] = [];
  for (const annotation of annotations) {
    switch (annotation.type) {
      case 'whiteClock':
      case 'blackClock': {
        const side = annotation.type === 'whiteClock' ? "White's" : "Black's";
        const time = formatClock(annotation.centiseconds);
        const title = game ? `${side} clock at the start: ${time}` : `${side} clock: ${time}`;
        parts.push(span('cbclock', title, `◷${time}`));
        break;
      }
      case 'timeSpent': {
        const time = formatTimeSpent(annotation);
        parts.push(span('cbtimespent', `Time spent: ${time}`, time));
        break;
      }
      case 'eval': {
        const text = formatEval(annotation);
        if (text !== null) {
          const depth = annotation.depth > 0 ? `, depth ${annotation.depth}` : '';
          parts.push(span('cbeval', `Engine evaluation${depth}`, text));
        }
        break;
      }
    }
  }
  return parts.join('');
}

import type { Annotation, AnnotationType } from '../model/annotations';
import { figurinesToUnicode } from './figurines';

/**
 * Markers for the annotations the notation doesn't show yet: a small label after the move naming
 * the kind, with the annotation's raw data shown when hovering over it. They make every annotation
 * visible until each kind gets a proper presentation.
 */

/** The label of each kind of annotation that is shown as a marker. */
const MARKER_LABELS: Partial<Record<AnnotationType, string>> = {
  whiteClock: 'clock',
  blackClock: 'clock',
  timeSpent: 'time',
  eval: 'eval',
  critical: 'critical',
  medals: 'medal',
  pawnStructure: 'pawns',
  piecePath: 'path',
  variationColor: 'var color',
  videoStreamTime: 'video',
  webLink: 'link',
  quote: 'quote',
  training: 'training',
  correspondence: 'corr',
  raw: 'raw',
};

/** A marker: its label and the details shown when hovering over it. */
export interface AnnotationMarker {
  label: string;
  details: string;
}

/**
 * The markers of the annotations of a move that the notation doesn't show.
 *
 * @param hasGlyph whether a NAG is shown as a glyph; the others get a marker
 */
export function annotationMarkers(
  annotations: readonly Annotation[],
  hasGlyph: (nag: number) => boolean
): AnnotationMarker[] {
  const markers: AnnotationMarker[] = [];
  for (const annotation of annotations) {
    switch (annotation.type) {
      case 'textBefore':
      case 'textAfter':
        // Shown, or hidden by the choice of languages
        break;
      case 'symbols': {
        const missing = annotation.nags.filter((nag) => !hasGlyph(nag));
        if (missing.length > 0) {
          markers.push({ label: missing.map((nag) => `$${nag}`).join(' '), details: details(annotation) });
        }
        break;
      }
      case 'squares':
      case 'arrows':
        break;
      case 'critical':
        // Shown by the color of the move, unless it's of no phase
        if (annotation.phase === 'none') {
          markers.push({ label: 'critical', details: details(annotation) });
        }
        break;
      default:
        markers.push({ label: MARKER_LABELS[annotation.type] ?? annotation.type, details: details(annotation) });
    }
  }
  return markers;
}

/** The markers as HTML. */
export function annotationMarkersHtml(markers: readonly AnnotationMarker[]): string {
  return markers
    .map(
      (marker) =>
        `<span class="cbanno-marker" title="${escapeHtml(marker.details)}">${escapeHtml(marker.label)}</span>`
    )
    .join('');
}

const MAX_TEXT = 300;

/** The raw data of an annotation, one field per line, with long values cut short. */
function details(annotation: Annotation): string {
  const lines: string[] = [annotation.type];
  for (const [field, value] of Object.entries(annotation)) {
    if (field === 'type') continue;
    let text = typeof value === 'string' ? figurinesToUnicode(value) : JSON.stringify(value);
    if (field === 'data' && typeof value === 'string') {
      text = `${Math.floor((value.length * 3) / 4)} bytes, base64 ${value.slice(0, 48)}${value.length > 48 ? '…' : ''}`;
    } else if (text.length > MAX_TEXT) {
      text = text.slice(0, MAX_TEXT) + '…';
    }
    lines.push(`${field}: ${text}`);
  }
  return lines.join('\n');
}

function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

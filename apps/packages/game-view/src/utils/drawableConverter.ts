import type { Annotation, AnnotationColor, ColoredArrow, ColoredSquare } from '../model/annotations';
import { findAnnotation, replaceAnnotation } from '../model/annotations';

// DrawShape type matching Chessground's autoShapes format
// Note: This matches the DrawShape interface in react-chessground.d.ts
// We keep it separate here since module declarations can't export types for import
type Square = string;

export interface DrawShape {
  orig: Square;
  dest?: Square;
  brush?: string;
  modifiers?: {
    hilite?: boolean;
    lineWidth?: number;
    [key: string]: unknown;
  };
}

/** The brush of the arrow showing the last move, which is not an annotation. */
export const LAST_MOVE_BRUSH = 'lastMove';

interface Brush {
  key: string;
  color: string;
  opacity: number;
  lineWidth: number;
}

/**
 * The brushes the board draws annotations with: one per color, named like the color, so a shape
 * drawn on the board says which color it has. Green, red, blue and yellow are the ones that can be
 * drawn with the mouse (with shift and alt), the others come from ChessBase.
 */
export const ANNOTATION_BRUSHES: Record<AnnotationColor | typeof LAST_MOVE_BRUSH, Brush> = {
  green: { key: 'g', color: '#15781B', opacity: 1, lineWidth: 10 },
  red: { key: 'r', color: '#882020', opacity: 1, lineWidth: 10 },
  blue: { key: 'b', color: '#003088', opacity: 1, lineWidth: 10 },
  yellow: { key: 'y', color: '#e68f00', opacity: 1, lineWidth: 10 },
  cyan: { key: 'c', color: '#0097a7', opacity: 1, lineWidth: 10 },
  orange: { key: 'o', color: '#e65100', opacity: 1, lineWidth: 10 },
  none: { key: 'n0', color: '#4a4a4a', opacity: 1, lineWidth: 10 },
  not_used: { key: 'n1', color: '#4a4a4a', opacity: 1, lineWidth: 10 },
  unknown_5: { key: 'n5', color: '#4a4a4a', opacity: 1, lineWidth: 10 },
  unknown_6: { key: 'n6', color: '#4a4a4a', opacity: 1, lineWidth: 10 },
  [LAST_MOVE_BRUSH]: { key: 'lm', color: '#e6c200', opacity: 0.7, lineWidth: 10 },
};

function isAnnotationColor(brush: string | undefined): brush is AnnotationColor {
  return brush !== undefined && brush !== LAST_MOVE_BRUSH && brush in ANNOTATION_BRUSHES;
}

/** The colored squares and arrows of a move as shapes on the board. */
export function annotationsToShapes(annotations: readonly Annotation[]): DrawShape[] {
  const squares: DrawShape[] = (findAnnotation(annotations, 'squares')?.squares ?? []).map((s) => ({
    orig: s.square,
    brush: s.color,
    modifiers: { hilite: true },
  }));
  const arrows: DrawShape[] = (findAnnotation(annotations, 'arrows')?.arrows ?? []).map((a) => ({
    orig: a.from,
    dest: a.to,
    brush: a.color,
  }));
  return [...squares, ...arrows];
}

/** The annotations of a move with its colored squares and arrows replaced by the shapes. */
export function shapesToAnnotations(annotations: readonly Annotation[], shapes: DrawShape[]): Annotation[] {
  const squares: ColoredSquare[] = [];
  const arrows: ColoredArrow[] = [];
  for (const shape of shapes) {
    const color = isAnnotationColor(shape.brush) ? shape.brush : 'green';
    if (shape.dest) {
      arrows.push({ color, from: shape.orig, to: shape.dest });
    } else {
      squares.push({ color, square: shape.orig });
    }
  }
  let result = replaceAnnotation(annotations, 'squares', squares.length > 0 ? { type: 'squares', squares } : null);
  result = replaceAnnotation(result, 'arrows', arrows.length > 0 ? { type: 'arrows', arrows } : null);
  return result;
}

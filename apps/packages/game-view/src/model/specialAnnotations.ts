import type { Annotation, AnnotationOf } from './annotations';
import { findAnnotation } from './annotations';

/**
 * The special annotations of a move that are set without anything more to give: a critical
 * position, the pawn structure and the path of the piece moved.
 */

export type CriticalPhase = 'opening' | 'middlegame' | 'endgame';

// The value ChessBase stores in every pawn structure and piece path annotation
const CHESSBASE_TYPE = 3;

/** The annotations without those of a kind. */
function without(annotations: readonly Annotation[], type: Annotation['type']): Annotation[] {
  return annotations.filter((a) => a.type !== type);
}

/** The phase of the critical position after the move, if it's one. */
export function criticalPhase(annotations: readonly Annotation[]): CriticalPhase | null {
  const phase = findAnnotation(annotations, 'critical')?.phase;
  return phase && phase !== 'none' ? phase : null;
}

/** The annotations with the position marked critical in a phase, or no longer if it was. */
export function toggleCritical(annotations: readonly Annotation[], phase: CriticalPhase): Annotation[] {
  const rest = without(annotations, 'critical');
  return criticalPhase(annotations) === phase ? rest : [...rest, { type: 'critical', phase }];
}

/** The annotations with the pawn structure shown, or no longer if it was. */
export function togglePawnStructure(annotations: readonly Annotation[]): Annotation[] {
  const rest = without(annotations, 'pawnStructure');
  return findAnnotation(annotations, 'pawnStructure')
    ? rest
    : [...rest, { type: 'pawnStructure', pawnStructureType: CHESSBASE_TYPE }];
}

/** The annotations with the path of the piece on a square shown, or no longer if it was. */
export function togglePiecePath(annotations: readonly Annotation[], square: string): Annotation[] {
  const rest = without(annotations, 'piecePath');
  const path: AnnotationOf<'piecePath'> = { type: 'piecePath', pathType: CHESSBASE_TYPE, square };
  return findAnnotation(annotations, 'piecePath') ? rest : [...rest, path];
}

/**
 * The annotations of a game as the server sends them, next to the movetext: each belongs to a
 * move, given by its index in the order the moves appear in the movetext (see GameTree), or to
 * the game as a whole (GAME_ANNOTATION_INDEX). Mirrors se.yarin.morphy.model.AnnotationDto.
 */

/** The index of the game as a whole, before the first move. */
export const GAME_ANNOTATION_INDEX = -1;

/**
 * The colors of squares and arrows. ChessBase stores a few more it gives no names to, which are
 * kept as they are.
 */
export type AnnotationColor =
  | 'green'
  | 'yellow'
  | 'red'
  | 'blue'
  | 'cyan'
  | 'orange'
  | 'none'
  | 'not_used'
  | 'unknown_5'
  | 'unknown_6';

export interface ColoredSquare {
  color: AnnotationColor;
  square: string;
}

export interface ColoredArrow {
  color: AnnotationColor;
  from: string;
  to: string;
}

/** Every kind of annotation, without the index of its move. */
export type Annotation =
  /** language: the IOC code, like 'ENG', when given; unknown: kept by ChessBase. */
  | { type: 'textBefore'; text: string; language?: string; unknown?: number }
  | { type: 'textAfter'; text: string; language?: string; unknown?: number }
  /** The NAG numbers, like 5 for !?. */
  | { type: 'symbols'; nags: number[] }
  | { type: 'squares'; squares: ColoredSquare[] }
  | { type: 'arrows'; arrows: ColoredArrow[] }
  /** The time left after the move, in hundredths of a second. */
  | { type: 'whiteClock'; centiseconds: number }
  | { type: 'blackClock'; centiseconds: number }
  | { type: 'timeSpent'; hours: number; minutes: number; seconds: number; unknown?: number }
  /** evalType 0: eval in hundredths of a pawn; 1: moves to mate. */
  | { type: 'eval'; eval: number; evalType: number; depth: number }
  | { type: 'critical'; phase: 'opening' | 'middlegame' | 'endgame' | 'none' }
  | { type: 'medals'; medals: string[] }
  | { type: 'pawnStructure'; pawnStructureType: number }
  | { type: 'piecePath'; pathType: number; square: string }
  /** color: '#rrggbb'. */
  | { type: 'variationColor'; color: string; onlyMoves: boolean; onlyMainline: boolean }
  | { type: 'videoStreamTime'; time: number }
  | { type: 'webLink'; url: string; text: string }
  | { type: 'quote'; header: Record<string, string>; moves?: string; fen?: string; unknown?: number }
  /** The binary kinds, base64 encoded. */
  | { type: 'training' | 'correspondence'; data: string }
  | { type: 'raw'; annotationType: number; data: string; invalid: boolean };

export type AnnotationType = Annotation['type'];

/** An annotation of a kind. */
export type AnnotationOf<T extends AnnotationType> = Extract<Annotation, { type: T }>;

/** An annotation as the server sends it, with the index of its move. */
export type AnnotationDto = Annotation & { move: number };

/** The first annotation of a kind, if there is one. */
export function findAnnotation<T extends AnnotationType>(
  annotations: readonly Annotation[],
  type: T
): AnnotationOf<T> | undefined {
  return annotations.find((a): a is AnnotationOf<T> => a.type === type);
}

/** The annotations of a kind. */
export function filterAnnotations<T extends AnnotationType>(
  annotations: readonly Annotation[],
  type: T
): AnnotationOf<T>[] {
  return annotations.filter((a): a is AnnotationOf<T> => a.type === type);
}

/**
 * The annotations with the one of a kind replaced, where it was, or added at the end; a null
 * annotation removes those of the kind.
 */
export function replaceAnnotation<T extends AnnotationType>(
  annotations: readonly Annotation[],
  type: T,
  annotation: AnnotationOf<T> | null
): Annotation[] {
  const index = annotations.findIndex((a) => a.type === type);
  const rest = annotations.filter((a) => a.type !== type);
  if (!annotation) return rest;
  const at = index < 0 ? rest.length : index;
  return [...rest.slice(0, at), annotation, ...rest.slice(at)];
}

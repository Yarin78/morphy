import type { Annotation, AnnotationOf } from './annotations';
import { hasDiagram, withoutDiagrams } from '../utils/diagram';

/** The two kinds of comments of a move: before it and after it. */
export type CommentType = 'textBefore' | 'textAfter';

/** Whether a comment is shown: in no language, or in one of those shown. */
export function isCommentShown(comment: { language?: string }, languages: ReadonlySet<string>): boolean {
  return !comment.language || languages.has(comment.language);
}

/**
 * The comment of a kind in a language, null for none, that is edited, or undefined if there is
 * none, when a new one is added instead.
 */
export function editedComment(
  annotations: readonly Annotation[],
  type: CommentType,
  language: string | null
): AnnotationOf<CommentType> | undefined {
  return annotations.find(
    (a): a is AnnotationOf<CommentType> => a.type === type && (a.language ?? null) === language
  );
}

/**
 * The annotations with the comment of a kind in a language, null for none, given new text: the
 * comment replaced, or added if there was none, or removed if the text is empty.
 */
export function withComment(
  annotations: readonly Annotation[],
  type: CommentType,
  language: string | null,
  text: string
): Annotation[] {
  const comment = editedComment(annotations, type, language);
  const trimmed = text.trim();
  if (!comment) {
    if (!trimmed) return [...annotations];
    return [...annotations, language ? { type, text: trimmed, language } : { type, text: trimmed }];
  }
  return trimmed
    ? annotations.map((a) => (a === comment ? { ...comment, text: trimmed } : a))
    : annotations.filter((a) => a !== comment);
}

/** What a comment has to ask for a diagram, where it is, as ChessBase writes it. */
export const DIAGRAM_TEXT = '[#]';

/**
 * The annotations with a diagram of the position after the move, or without it if it has one: one
 * asked for at the start of its comment after in no language, or in a new one; or the diagrams of
 * that comment taken away, and the comment too if nothing else is left of it.
 */
export function toggleDiagram(annotations: readonly Annotation[]): Annotation[] {
  const comment = editedComment(annotations, 'textAfter', null);
  if (comment && hasDiagram(comment.text)) {
    return withComment(annotations, 'textAfter', null, withoutDiagrams(comment.text));
  }
  return withComment(annotations, 'textAfter', null, comment ? `${DIAGRAM_TEXT} ${comment.text}` : DIAGRAM_TEXT);
}

/** Whether the comment after the move in no language asks for a diagram. */
export function hasDiagramComment(annotations: readonly Annotation[]): boolean {
  return hasDiagram(editedComment(annotations, 'textAfter', null)?.text ?? '');
}

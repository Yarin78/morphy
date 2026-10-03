import type { Annotation, AnnotationOf } from './annotations';

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

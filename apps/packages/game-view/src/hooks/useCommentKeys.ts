import { useEffect } from 'react';
import { isTyped } from '../utils/keys';
import type { CommentType } from '../model/comments';

/** The keys that edit a comment of the current move, and the kind each one edits. */
export const COMMENT_KEYS: Record<string, CommentType> = {
  a: 'textAfter',
  b: 'textBefore',
};

/**
 * Lets the keyboard start editing a comment of the current move: 'a' after it and 'b' before it.
 * Keys typed into a field, and keys pressed with Cmd or Ctrl, are left alone; see isTyped.
 */
export function useCommentKeys(enabled: boolean, onEdit: (type: CommentType) => void): void {
  useEffect(() => {
    if (!enabled) return;

    const handleKeyDown = (e: KeyboardEvent) => {
      const type = COMMENT_KEYS[e.key];
      if (type === undefined || !isTyped(e, e.key)) return;
      const activeElement = document.activeElement;
      if (
        activeElement instanceof HTMLInputElement ||
        activeElement instanceof HTMLTextAreaElement ||
        activeElement instanceof HTMLSelectElement ||
        (activeElement instanceof HTMLElement && activeElement.isContentEditable)
      ) {
        return;
      }
      e.preventDefault();
      onEdit(type);
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [enabled, onEdit]);
}

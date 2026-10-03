import { useEffect } from 'react';
import type { CommentType } from '../model/comments';

/** The keys that edit a comment of the current move, and the kind each one edits. */
export const COMMENT_KEYS: Record<string, CommentType> = {
  a: 'textAfter',
  b: 'textBefore',
};

/**
 * Lets the keyboard start editing a comment of the current move: 'a' after it and 'b' before it.
 * Keys typed into a field, and keys pressed with Ctrl, Cmd or Alt, are left alone.
 */
export function useCommentKeys(enabled: boolean, onEdit: (type: CommentType) => void): void {
  useEffect(() => {
    if (!enabled) return;

    const handleKeyDown = (e: KeyboardEvent) => {
      const type = COMMENT_KEYS[e.key];
      if (type === undefined || e.ctrlKey || e.metaKey || e.altKey) return;
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

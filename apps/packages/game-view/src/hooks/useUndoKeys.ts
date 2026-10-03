import { useEffect } from 'react';
import { MAC } from '../components/moveActions';

/** The keys that undo and redo an edit, as shown. */
export const UNDO_SHORTCUTS = {
  undo: MAC ? '⌘Z' : 'Ctrl+Z',
  redo: MAC ? '⌘⇧Z' : 'Ctrl+Y',
};

/**
 * Lets the keyboard undo and redo the edits to the moves: Cmd+Z and Cmd+Shift+Z on a Mac, and
 * Ctrl+Z and Ctrl+Y or Ctrl+Shift+Z elsewhere. Keys typed into a field are left alone, so they
 * undo the typing there.
 */
export function useUndoKeys(enabled: boolean, onUndo: () => void, onRedo: () => void): void {
  useEffect(() => {
    if (!enabled) return;

    const handleKeyDown = (e: KeyboardEvent) => {
      const activeElement = document.activeElement;
      if (
        activeElement instanceof HTMLInputElement ||
        activeElement instanceof HTMLTextAreaElement ||
        activeElement instanceof HTMLSelectElement ||
        (activeElement instanceof HTMLElement && activeElement.isContentEditable)
      ) {
        return;
      }
      if (!(MAC ? e.metaKey : e.ctrlKey) || e.altKey) return;
      const key = e.key.toLowerCase();
      const redo = (key === 'z' && e.shiftKey) || (!MAC && key === 'y' && !e.shiftKey);
      const undo = key === 'z' && !e.shiftKey;
      if (!undo && !redo) return;
      e.preventDefault();
      e.stopImmediatePropagation();
      if (redo) onRedo();
      else onUndo();
    };

    window.addEventListener('keydown', handleKeyDown, true);
    return () => window.removeEventListener('keydown', handleKeyDown, true);
  }, [enabled, onUndo, onRedo]);
}

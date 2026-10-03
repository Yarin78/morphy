import { useEffect } from 'react';
import { isTyped } from '../utils/keys';

/**
 * Lets a key, a character typed, do something. Keys typed into a field, and keys pressed with Cmd
 * or Ctrl, are left alone; see isTyped.
 */
export function useKeyShortcut(enabled: boolean, key: string, action: () => void): void {
  useEffect(() => {
    if (!enabled) return;

    const handleKeyDown = (e: KeyboardEvent) => {
      if (!isTyped(e, key)) return;
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
      action();
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [enabled, key, action]);
}

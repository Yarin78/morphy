import { useEffect } from 'react';
import type { MoveAction } from '../components/moveActions';

/**
 * Lets the keyboard make the changes to the moves from the current move, each by its key. Keys
 * typed into a field are left alone. The keys come before those going through the moves, as
 * Cmd+↑ would otherwise go up a line too.
 *
 * @param actions the changes that can be made from the current move, when a key is pressed
 */
export function useMoveActionKeys(enabled: boolean, actions: () => readonly MoveAction[]): void {
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
      const action = actions().find((a) => a.isKey?.(e));
      if (!action) return;
      e.preventDefault();
      e.stopImmediatePropagation();
      if (!action.disabled) action.run();
    };

    window.addEventListener('keydown', handleKeyDown, true);
    return () => window.removeEventListener('keydown', handleKeyDown, true);
  }, [enabled, actions]);
}

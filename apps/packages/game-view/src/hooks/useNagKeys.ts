import { useEffect } from 'react';

/** The keys that toggle a symbol on the current move, and the NAG each one toggles. */
const NAG_KEYS: Record<string, number> = {
  '!': 1, // good move
  '?': 2, // mistake
  '=': 10, // equal
};

/**
 * Lets the keyboard toggle the most common symbols on the current move: '!', '?' and '='.
 * Keys typed into a field, and keys pressed with Ctrl, Cmd or Alt, are left alone.
 */
export function useNagKeys(enabled: boolean, onToggle: (nag: number) => void): void {
  useEffect(() => {
    if (!enabled) return;

    const handleKeyDown = (e: KeyboardEvent) => {
      const nag = NAG_KEYS[e.key];
      if (nag === undefined || e.ctrlKey || e.metaKey || e.altKey) return;
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
      onToggle(nag);
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [enabled, onToggle]);
}

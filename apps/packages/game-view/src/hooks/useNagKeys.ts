import { useEffect } from 'react';
import { isTyped } from '../utils/keys';

/** The keys that give the current move a symbol, and the NAG each one gives on its own. */
export const NAG_KEYS: Record<string, number> = {
  '!': 1, // good move
  '?': 2, // mistake
  '=': 10, // equal
};

// The keys that are pressed to type others, which don't break a sequence of keys typed
const MODIFIER_KEYS = new Set(['Shift', 'Alt', 'AltGraph', 'Control', 'Meta', 'CapsLock']);

/** Whether a key is typed into a field, where it's the field's. */
function inField(): boolean {
  const activeElement = document.activeElement;
  return (
    activeElement instanceof HTMLInputElement ||
    activeElement instanceof HTMLTextAreaElement ||
    activeElement instanceof HTMLSelectElement ||
    (activeElement instanceof HTMLElement && activeElement.isContentEditable)
  );
}

interface NagKeyHandlers {
  /** '=' toggles its symbol. */
  onToggle: (nag: number) => void;
  /** '!' or '?' is typed, on its own or after the other keys typed just before; see typeMoveComment. */
  onType: (key: '!' | '?') => void;
  /** Anything else was done, a key pressed or the mouse clicked, which ends what was being typed. */
  onInterrupt: () => void;
}

/**
 * Lets the keyboard give the current move the most common symbols: '!' and '?', which can be typed
 * two in a row, like '!?', and '='. Keys typed into a field, and keys pressed with Cmd or Ctrl, are
 * left alone; see isTyped.
 */
export function useNagKeys(enabled: boolean, { onToggle, onType, onInterrupt }: NagKeyHandlers): void {
  useEffect(() => {
    if (!enabled) return;

    const handleKeyDown = (e: KeyboardEvent) => {
      if (MODIFIER_KEYS.has(e.key)) return;
      const nag = NAG_KEYS[e.key];
      if (nag === undefined || !isTyped(e, e.key) || inField()) {
        onInterrupt();
        return;
      }
      e.preventDefault();
      if (e.key === '!' || e.key === '?') onType(e.key);
      else {
        onInterrupt();
        onToggle(nag);
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    window.addEventListener('mousedown', onInterrupt);
    return () => {
      window.removeEventListener('keydown', handleKeyDown);
      window.removeEventListener('mousedown', onInterrupt);
    };
  }, [enabled, onToggle, onType, onInterrupt]);
}

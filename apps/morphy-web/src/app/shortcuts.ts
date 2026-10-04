// Keyboard shortcuts, as menus and buttons show them

/** Whether this is a Mac, where Cmd takes Ctrl's part */
export const MAC = /Mac|iPhone|iPad/.test(navigator.platform);

/** A shortcut as shown: with the Mac's symbols on a Mac. */
export function shortcutLabel(shortcut: string): string {
  if (!MAC) return shortcut.replace('Cmd', 'Ctrl');
  const symbols: Record<string, string> = { Ctrl: '⌃', Alt: '⌥', Shift: '⇧', Cmd: '⌘' };
  return shortcut
    .split('+')
    .map((part) => symbols[part] ?? part)
    .join('');
}

/**
 * Whether a key pressed typed one of some characters, as a shortcut. On many keyboard layouts a
 * character needs Option (Mac) or AltGr (Windows, seen as Ctrl+Alt) to be typed, like ']' on a
 * Swedish one, so Alt is allowed; Cmd, and Ctrl without Alt, make it another shortcut.
 */
export function isTyped(
  e: Pick<KeyboardEvent, 'key' | 'metaKey' | 'ctrlKey' | 'altKey'>,
  ...keys: string[]
): boolean {
  return keys.includes(e.key) && !e.metaKey && (!e.ctrlKey || e.altKey);
}

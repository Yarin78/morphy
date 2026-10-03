import { describe, expect, it } from 'vitest';
import { isTyped } from './keys';

const key = (k: string, mods: { metaKey?: boolean; ctrlKey?: boolean; altKey?: boolean } = {}) => ({
  key: k,
  metaKey: false,
  ctrlKey: false,
  altKey: false,
  ...mods,
});

describe('typed shortcut keys', () => {
  it('are the character, typed with Option or AltGr too', () => {
    expect(isTyped(key(']'), ']')).toBe(true);
    // ']' on a Swedish Mac (Option+9) and on Windows (AltGr+9)
    expect(isTyped(key(']', { altKey: true }), ']')).toBe(true);
    expect(isTyped(key(']', { ctrlKey: true, altKey: true }), ']')).toBe(true);
  });

  it('are not the character with Cmd, or Ctrl alone', () => {
    expect(isTyped(key(']', { metaKey: true }), ']')).toBe(false);
    expect(isTyped(key(']', { ctrlKey: true }), ']')).toBe(false);
    expect(isTyped(key('['), ']')).toBe(false);
  });
});

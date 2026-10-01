/**
 * Reads the values of PGN tags as a Chess instance has them, by name: a tag that isn't there is
 * '', and '?', '????' and so on are PGN's placeholders for an unknown value. None of them is a
 * value.
 */
export function tagReader(tag: (name: string) => string) {
  const text = (name: string): string | undefined => {
    const value = tag(name).trim();
    return value && !/^\?+$/.test(value) ? value : undefined;
  };
  return {
    text,
    /** A whole number above zero. */
    number(name: string): number | undefined {
      const value = text(name);
      return value && /^\d+$/.test(value) && +value > 0 ? +value : undefined;
    },
    /** A flag, which is '1' when it's set. */
    flag(name: string): true | undefined {
      return text(name) === '1' || undefined;
    },
    /** An entity's id: undefined without one, null if it isn't a number. */
    id(name: string): number | null | undefined {
      const value = text(name);
      return value === undefined ? undefined : /^\d+$/.test(value) ? +value : null;
    },
  };
}

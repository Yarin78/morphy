/**
 * A player of the game, as edited in the Edit Game Info dialog. With an id, it's an existing player
 * in the database; without one, the player is found by name, or created, when the game is saved.
 */
export interface PlayerInfo {
  id: number | null;
  /** "Lastname, Firstname", or just a last name; see splitPlayerName. */
  name: string;
  /** The number of games of an existing player. */
  gameCount?: number;
}

/** How the Edit Game Info dialog finds existing players. */
export interface PlayerService {
  /** Players whose "Lastname, Firstname" starts with the text, those with the most games first. */
  search(text: string): Promise<PlayerInfo[]>;
}

/**
 * Not standard PGN tags: the ids of the existing players, understood only by whoever saves the
 * game.
 */
export const PLAYER_ID_TAGS = { white: 'WhiteId', black: 'BlackId' } as const;

/**
 * Splits "Lastname, Firstname" at the first comma; without a comma, it's all last name. A first
 * name can have commas of its own.
 */
export function splitPlayerName(name: string): { lastName: string; firstName: string } {
  const comma = name.indexOf(',');
  return comma < 0
    ? { lastName: name.trim(), firstName: '' }
    : { lastName: name.slice(0, comma).trim(), firstName: name.slice(comma + 1).trim() };
}

export function joinPlayerName(lastName: string, firstName: string): string {
  return firstName ? `${lastName}, ${firstName}` : lastName;
}

/** The name as it's stored: split and joined again, so "Carlsen ,Magnus" is "Carlsen, Magnus". */
export function normalizePlayerName(name: string): string {
  const { lastName, firstName } = splitPlayerName(name);
  return joinPlayerName(lastName, firstName);
}

import type { GameMoves } from '../model/GameTree';

export interface GameHeader {
  id: string;
  white: string;
  whiteElo?: number;
  black: string;
  blackElo?: number;
  result: string;
  date: string;
  event?: string;
  site?: string;
  round?: string;
  annotator?: string;
  sourceTitle?: string;
  /** ISO-8601 instant when known (e.g. synced online games); absent for date-only headers. */
  playedAt?: string | null;
}

export interface ChessGame {
  header: GameHeader;
  /** The PGN tags of the game, in order; the Edit Game Info dialog reads and writes them. */
  tags: [string, string][];
  /** The moves and their annotations, as the server sends them; absent for a new game. */
  moves?: GameMoves;
}

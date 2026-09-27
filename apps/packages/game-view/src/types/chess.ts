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
  pgn: string;
}

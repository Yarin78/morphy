// Hand-written mirror of morphy-service's JSON DTOs (se.yarin.morphy.model.*),
// same convention as apps/search-tester/src/api/types.ts - no codegen.
//
// Verified against the actual Java records and Jackson defaults, not the
// stale morphy-service/GAME_DTO_USAGE.md doc (which shows a fictitious
// /api/v1/... path and a "WIN_WHITE" result value that doesn't match the
// real enum):
//   - `result` serializes as the Java enum constant name (e.g. "WHITE_WINS"),
//     not a PGN symbol.
//   - `date` (and other Date fields) serializes as {year, month, day}, not
//     a string.

export type GameResultDto =
  | 'WHITE_WINS'
  | 'BLACK_WINS'
  | 'DRAW'
  | 'NOT_FINISHED'
  | 'WHITE_WINS_ON_FORFEIT'
  | 'BLACK_WINS_ON_FORFEIT'
  | 'DRAW_ON_FORFEIT'
  | 'BOTH_LOST';

export interface DateDto {
  year: number;
  month: number;
  day: number;
}

export interface PlayerDto {
  id: number | null;
  lastName?: string;
  firstName?: string;
  gameCount?: number;
  fideId?: number;
  chessBaseId?: number;
}

export interface TeamDto {
  id: number | null;
  title?: string;
  teamNumber?: number;
  season?: boolean;
  year?: number;
  nation?: string;
  gameCount?: number;
}

export interface TournamentDto {
  id: number | null;
  title?: string;
  startDate?: DateDto;
  endDate?: DateDto;
  place?: string;
  nation?: string;
  category?: number;
  categoryRoman?: string;
  rounds?: number;
  type?: string;
  timeControl?: string;
  typeCombined?: string;
  complete?: boolean;
  teamTournament?: boolean;
  tiebreakRules?: string[];
  latitude?: number;
  longitude?: number;
  gameCount?: number;
}

export interface SourceDto {
  id: number | null;
  title?: string;
  publisher?: string;
  publication?: DateDto;
  date?: DateDto;
  version?: number;
  quality?: string;
  gameCount?: number;
}

export interface AnnotatorDto {
  id: number | null;
  name?: string;
  gameCount?: number;
}

export interface GameTagDto {
  id: number | null;
  title?: string;
  languages?: string;
  languageCount?: number;
  englishTitle?: string;
  germanTitle?: string;
  frenchTitle?: string;
  spanishTitle?: string;
  italianTitle?: string;
  dutchTitle?: string;
  slovenianTitle?: string;
  resTitle?: string;
  gameCount?: number;
}

export interface GameMovesDto {
  /** Movetext only - no [Tag "value"] headers. */
  pgn?: string;
  /** Non-null only for setup positions (non-standard starting position). */
  fen?: string;
}

export interface GameTextDto {
  contents?: string;
}

export interface GameDto {
  id: number | null;
  type: string;
  textTitle?: string;
  whitePlayer?: PlayerDto;
  whiteElo?: number;
  blackPlayer?: PlayerDto;
  blackElo?: number;
  whiteTeam?: TeamDto;
  blackTeam?: TeamDto;
  result: GameResultDto;
  date: DateDto;
  eco?: string;
  round?: number;
  subRound?: number;
  lineEvaluation?: string;
  tournament?: TournamentDto;
  source?: SourceDto;
  annotator?: AnnotatorDto;
  gameTag?: GameTagDto;
  medals?: string[];
  deleted?: boolean;
  topGame?: boolean;
  setupPosition?: boolean;
  variant?: string;
  noMoves?: number;
  notation?: string;
  variationMoves?: number;
  ait?: string;
  vcs?: string;
  finalMaterial?: string;
  gameVersion?: number;
  creationTimestamp?: number;
  lastChanged?: string;
  moves?: GameMovesDto;
  text?: GameTextDto;
  extraTags?: Record<string, string>;
}

// Identical shape to search-tester's own DatabaseResponse/DatabaseListResponse
// (confirmed against DatabaseController).
export interface DatabaseResponse {
  id: string;
  displayName: string;
  path: string;
}

export interface DatabaseListResponse {
  databases: DatabaseResponse[];
}

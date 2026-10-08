/**
 * Types matching the morphy-service REST API.
 *
 * The DTOs below (GameDto and everything it embeds) are a hand-written mirror of
 * morphy-service's JSON DTOs (se.yarin.morphy.model.*), no codegen - verified against the
 * actual Java records and Jackson's defaults, not the stale morphy-service/GAME_DTO_USAGE.md
 * doc (which shows a fictitious /api/v1/... path and a "WIN_WHITE" result value that doesn't
 * match the real enum):
 *   - `result` serializes as the Java enum constant name (e.g. "WHITE_WINS"), not a PGN symbol.
 *   - `date` (and other Date fields) serializes as {year, month, day}, not a string.
 * This is also what a search response's embedded entities look like (confirmed against a
 * running service) - they're not trimmed down to id+main-field as search results.
 */

import type { AnnotationDto } from 'game-view';

export interface DatabaseResponse {
  id: string;
  displayName: string;
  path: string;
  /** A read-only database can be searched, but games can't be saved to it */
  readOnly: boolean;
}

/**
 * A position index, as the service defines it: the games of a database (or those matching a
 * filter) by the positions they reached, to search a board's position in.
 */
export interface PositionIndexResponse {
  id: string;
  /** A short name, as its pill shows it */
  name: string;
  /** The database whose games it holds, which they open in */
  databaseId: string;
  /** The games it holds, in the game search's language; empty for every game */
  filter: string;
  /** Whether it can be searched, or is to be built, or being built */
  status: 'ready' | 'missing' | 'stale' | 'building' | 'failed';
  /** What's to know about the status: the build's progress, why it failed, how to build it */
  message?: string;
  /** The games it holds, when built */
  games?: number;
  builtAt?: string;
}

export interface DatabaseListResponse {
  databases: DatabaseResponse[];
}

export interface SortFieldOption {
  name: string;
  defaultDirection: string;
}

export interface FilterOptionsResponse {
  defaultField: string;
  fields: string[];
  sortFields: SortFieldOption[];
}

export interface GameSearchRequest {
  offset?: number;
  limit?: number;
  /** Sort spec: field with optional +/- prefix (e.g. "+id", "-date") */
  sortBy?: string;
  includeMoves?: boolean;
  includeText?: boolean;
  filter?: string;
  /** A PGN-style result filter value (e.g. "1-0"), not the GameDto.result wire value. */
  result?: string;
  dateFrom?: string;
  dateTo?: string;
  ecoCode?: string;
  round?: number;
  ratingMin?: number;
  ratingMax?: number;
  ratingMode?: string;
  playerId?: number;
  playerPosition?: string;
  tournamentId?: number;
  annotatorId?: number;
  sourceId?: number;
  teamId?: number;
  teamPosition?: string;
  gameTagId?: number;
}

export interface SearchMetadata {
  appliedFilter: string | null;
  sortBy: string;
  executionTimeMs: number;
}

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

/** A time control: periods played one after the other. */
export interface TimeControlDto {
  periods: {
    /** The time the period starts with, in seconds. */
    seconds: number;
    /** Added after each move, in seconds. */
    increment: number;
    /** The number of moves of the period; none for the rest of the game. */
    moves?: number | null;
  }[];
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
  portugueseTitle?: string;
  resTitle?: string;
  gameCount?: number;
}

export interface GameMovesDto {
  /** Movetext only - no [Tag "value"] headers, comments, NAGs or result. */
  pgn?: string;
  /** Non-null only for setup positions (non-standard starting position). */
  fen?: string;
  /**
   * The annotations of the moves, each with the index of its move in the movetext (-1 for the
   * game as a whole); see game-view's AnnotationDto. Absent when there are none.
   */
  annotations?: AnnotationDto[];
}

export interface GameTextDto {
  contents?: string;
}

/** What kind of rating an elo is, from se.yarin.chess.EloType. */
export interface EloTypeDto {
  kind: 'INTERNATIONAL' | 'NATIONAL' | 'SERVER';
  timeControl: 'NORMAL' | 'BULLET' | 'BLITZ' | 'RAPID' | 'CORRESPONDENCE';
  nation?: string;
  name?: string;
}

export interface GameDto {
  id: number | null;
  type: string;
  textTitle?: string;
  whitePlayer?: PlayerDto;
  whiteElo?: number;
  /** Present with an elo: what kind of rating it is. */
  whiteEloType?: EloTypeDto;
  blackPlayer?: PlayerDto;
  blackElo?: number;
  blackEloType?: EloTypeDto;
  whiteTeam?: TeamDto;
  blackTeam?: TeamDto;
  result: GameResultDto;
  date: DateDto;
  eco?: string;
  round?: number;
  subRound?: number;
  /** The board in a team match; not stored by every format. */
  board?: number;
  lineEvaluation?: string;
  timeControl?: TimeControlDto;
  tournament?: TournamentDto;
  source?: SourceDto;
  annotator?: AnnotatorDto;
  gameTag?: GameTagDto;
  medals?: string[];
  deleted?: boolean;
  topGame?: boolean;
  setupPosition?: boolean;
  /** "Chess960" for a Chess960 game; absent for regular chess. */
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

/** Operator-level cost (estimates and optionally actuals). */
export interface OperatorCostDto {
  estimatedRows: number;
  estimatedDeserializations: number;
  estimatedPageReads: number;
  actualRows?: number | null;
  actualDeserializations?: number | null;
  actualPhysicalPageReads?: number | null;
  actualLogicalPageReads?: number | null;
  actualIsDuplicate?: boolean | null;
}

/** Node in a query plan graph. */
export interface QueryOperatorNodeDto {
  id: number;
  name: string;
  params: string;
  type: string;
  hasFullData: boolean;
  sorted: boolean;
  sortOrder: string;
  mayContainDuplicates: boolean;
  cost: OperatorCostDto;
}

/** Edge in a query plan graph. */
export interface QueryPlanEdgeDto {
  from: number;
  to: number;
}

/** Total cost for a query plan. */
export interface QueryCostDto {
  estimatedRows: number;
  estimatedPageReads: number;
  estimatedDeserializations: number;
  estimatedCpuCost: number;
  estimatedIOCost: number;
  estimatedTotalCost: number;
  actualRows?: number | null;
  actualPhysicalPageReads?: number | null;
  actualLogicalPageReads?: number | null;
  actualDeserializations?: number | null;
  actualWallClockTimeMs?: number | null;
}

/** Single query execution plan. */
export interface QueryPlanDto {
  label: string;
  nodes: QueryOperatorNodeDto[];
  edges: QueryPlanEdgeDto[];
  totalCost: QueryCostDto;
  executed: boolean;
  resultCount?: number | null;
  resultsDifferFromSelected?: boolean | null;
}

/** The query plans of a debug search. */
export interface QueryPlanDebugInfo {
  queryDescription: string;
  selectedPlanIndex: number;
  allPlansAgree?: boolean | null;
  plans: QueryPlanDto[];
}

export interface GameSearchResponse {
  games: GameDto[];
  count: number;
  totalCount: number | null;
  offset: number;
  limit: number;
  metadata: SearchMetadata;
}

/** Request for entity search (Players, Tournaments, etc.). sortBy uses +/- prefix (e.g. "+id", "-name"). */
export interface EntitySearchRequest {
  filter?: string | null;
  offset?: number | null;
  limit?: number | null;
  sortBy?: string | null;
}

/** Response from entity search endpoints. */
export interface EntitySearchResponse<T> {
  items: T[];
  count: number;
  totalCount: number | null;
  offset: number;
  limit: number;
  metadata: {
    appliedFilter?: string | null;
    sortBy: string;
    executionTimeMs: number;
  };
}

/** The stored bytes of one record behind a returned item, from one file. Base64 in JSON. */
export interface RawRecord {
  /** The file extension the record comes from, e.g. ".cbh". */
  file: string;
  bytes: string;
}

/** Response from the debug search endpoints: the normal result, its query plans and raw records. */
export interface DebugSearchResponse<R> {
  result: R;
  plans: QueryPlanDebugInfo;
  /** The raw records of every returned item, keyed by its id. */
  raw: Record<string, RawRecord[]>;
}

/** A player who played a move, among the strongest who did. */
export interface PositionMovePlayer {
  /** "Lastname, Firstname" */
  name: string;
  /** Their rating in the game they played the move in, if it had one */
  rating: number | null;
}

/** How often a move was played from a position, and how the games went on from there. */
export interface PositionMoveStats {
  /** The move, in SAN */
  san: string;
  /** The games that played it */
  games: number;
  whiteWins: number;
  draws: number;
  blackWins: number;
  /** The games since the response's recentSince year that played it, to tell how it's trending */
  recentGames: number;
  /** The year of the latest game that played it, if any game has a year */
  lastPlayed: number | null;
  /** The average rating of the players who played it, of those rated; null if none was */
  averageRating: number | null;
  /** Some of the highest rated players who played it, the highest first */
  topPlayers: PositionMovePlayer[];
}

/** The games of a reference database that reached a position, and what was played from it. */
export interface PositionSummary {
  fen: string;
  /** The games that reached the position, including those that ended there */
  games: number;
  whiteWins: number;
  draws: number;
  blackWins: number;
  /** The first year of the games that count as recent, in each move's recentGames */
  recentSince: number;
  /** The moves played from it, the most played first */
  moves: PositionMoveStats[];
}

/** A search of a reference database's games by position: a page of them, sorted as asked. */
export interface PositionSearchRequest {
  fen: string;
  sortBy?: string;
  offset?: number;
  limit?: number;
  /** Whether the games come with their moves, as in a game search */
  includeMoves?: boolean;
}

/** How a position search used its index. */
export interface PositionIndexState {
  /**
   * ready: used and up to date; stale: used, but its database has changed since it was built;
   * missing: none to use (not built, unreadable, or of another filter), so every game was played
   * through instead
   */
  status: 'ready' | 'stale' | 'missing';
  /** Why it's missing */
  message?: string | null;
  /** When stale, the games added to the database since it was built (0 if only changed or deleted) */
  missingGames?: number | null;
}

export interface PositionSearchResponse {
  indexId: string;
  /** The database the games are of, which they open in */
  databaseId: string;
  /** Whether the index was used, and how up to date it is */
  index: PositionIndexState;
  /** What was played from the position; with the first page only, null with the later ones */
  summary: PositionSummary | null;
  /** The page of the games that reached it */
  games: GameSearchResponse;
}

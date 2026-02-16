/**
 * Types matching the morphy-service REST API.
 */

export interface DatabaseResponse {
  id: string;
  displayName: string;
  path: string;
}

export interface DatabaseListResponse {
  databases: DatabaseResponse[];
}

export interface GameSearchRequest {
  offset?: number;
  limit?: number;
  sortBy?: string;
  order?: string;
  includeMoves?: boolean;
  includeText?: boolean;
  filter?: string;
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
  order: string;
  executionTimeMs: number;
}

/** Sub-entities in GameDto return minimal fields (id + main field) when embedded in search results. */
export interface GameDto {
  id: number;
  type?: string;
  whitePlayer?: { id: number; lastName?: string; firstName?: string };
  whiteElo?: number;
  blackPlayer?: { id: number; lastName?: string; firstName?: string };
  blackElo?: number;
  whiteTeam?: { id: number; title?: string };
  blackTeam?: { id: number; title?: string };
  result: string;
  date: string;
  eco?: string;
  round?: number;
  subRound?: number;
  lineEvaluation?: string;
  tournament?: { id: number; name?: string };
  source?: { id: number; title?: string };
  annotator?: { id: number; name?: string };
  gameTag?: { id: number; englishTitle?: string; germanTitle?: string; [key: string]: unknown };
  moves?: unknown;
  text?: unknown;
}

export interface GameSearchResponse {
  games: GameDto[];
  count: number;
  totalCount: number | null;
  offset: number;
  limit: number;
  metadata: SearchMetadata;
}

export interface PlayerDto {
  id: number;
  lastName?: string;
  firstName?: string;
  gameCount?: number;
}

export interface PlayerListResponse {
  players: PlayerDto[];
  count: number;
  nextCursor: string | null;
  hasMore: boolean;
}

export interface TournamentDto {
  id: number;
  name: string;
  startDate?: string;
  endDate?: string;
  site?: string;
  country?: string;
  category?: number;
  rounds?: number;
  type?: string;
  timeControl?: string;
  complete?: boolean;
  teamTournament?: boolean;
  gameCount?: number;
}

export interface TournamentListResponse {
  tournaments: TournamentDto[];
  count: number;
  nextCursor: string | null;
  hasMore: boolean;
}

export interface AnnotatorDto {
  id: number;
  name?: string;
  gameCount?: number;
}

export interface AnnotatorListResponse {
  annotators: AnnotatorDto[];
  count: number;
  nextCursor: string | null;
  hasMore: boolean;
}

export interface SourceDto {
  id: number;
  title?: string;
  publisher?: string;
  publication?: string;
  date?: string;
  version?: number;
  quality?: string;
  gameCount?: number;
}

export interface SourceListResponse {
  sources: SourceDto[];
  count: number;
  nextCursor: string | null;
  hasMore: boolean;
}

export interface TeamDto {
  id: number;
  title?: string;
  teamNumber?: number;
  season?: boolean;
  year?: number;
  nation?: string;
  gameCount?: number;
}

export interface TeamListResponse {
  teams: TeamDto[];
  count: number;
  nextCursor: string | null;
  hasMore: boolean;
}

export interface GameTagDto {
  id: number;
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

export interface GameTagListResponse {
  gameTags: GameTagDto[];
  count: number;
  nextCursor: string | null;
  hasMore: boolean;
}

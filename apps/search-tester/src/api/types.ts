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

/** Sub-entities in GameDto return minimal fields (id + main field) when embedded in search results. */
export interface GameDto {
  id: number;
  type?: string;
  textTitle?: string;
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
  tournament?: { id: number; title?: string };
  source?: { id: number; title?: string };
  annotator?: { id: number; name?: string };
  gameTag?: { id: number; englishTitle?: string; germanTitle?: string; [key: string]: unknown };
  medals?: string[];
  deleted?: boolean;
  topGame?: boolean;
  setupPosition?: boolean;
  noMoves?: number;
  notation?: string;
  variationMoves?: number;
  ait?: string;
  vcs?: string;
  finalMaterial?: string;
  gameVersion?: number;
  creationTimestamp?: number;
  lastChanged?: string;
  moves?: unknown;
  text?: unknown;
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

export interface PlayerDto {
  id: number;
  lastName?: string;
  firstName?: string;
  gameCount?: number;
}

export interface TournamentDto {
  id: number;
  title: string;
  startDate?: string;
  endDate?: string;
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

export interface AnnotatorDto {
  id: number;
  name?: string;
  gameCount?: number;
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

export interface TeamDto {
  id: number;
  title?: string;
  teamNumber?: number;
  season?: boolean;
  year?: number;
  nation?: string;
  gameCount?: number;
}

export interface GameTagDto {
  id: number;
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

import { Fragment } from 'react';
import type {
  AnnotatorDto,
  GameDto,
  GameTagDto,
  PlayerDto,
  SourceDto,
  TeamDto,
  TournamentDto,
} from './api/types';

interface Column<T> {
  key: string;
  label: string;
  render: (row: T) => React.ReactNode;
}

/** Row type that may include raw bytes from the API (when debugRawData is set). */
interface RowWithRawData {
  rawData?: string | number[];
  rawExtendedData?: string | number[];
  rawExtraData?: string | number[];
}

const HEX_BLOCK_SIZE = 16;   /* hex chars per block (8 bytes) */
const HEX_BLOCKS_PER_LINE = 8; /* line break after this many blocks (64 bytes per line) */
const NBSP = '\u00A0';

function rawPayloadToHex(payload: string | number[]): string {
  let bytes: number[];
  if (typeof payload === 'string') {
    const binary = atob(payload);
    bytes = Array.from(binary, (c) => c.charCodeAt(0));
  } else {
    bytes = payload;
  }
  const hex = bytes.map((b) => (b & 0xff).toString(16).padStart(2, '0')).join('');
  const blocks: string[] = [];
  for (let i = 0; i < hex.length; i += HEX_BLOCK_SIZE) {
    blocks.push(hex.slice(i, i + HEX_BLOCK_SIZE));
  }
  const lines: string[] = [];
  for (let i = 0; i < blocks.length; i += HEX_BLOCKS_PER_LINE) {
    const lineBlocks = blocks.slice(i, i + HEX_BLOCKS_PER_LINE);
    lines.push(lineBlocks.join(NBSP)); /* no break within 16-char block */
  }
  return lines.join('\n');
}

function getRawPayloads(row: unknown): (string | number[])[] {
  const r = row as RowWithRawData;
  const out: (string | number[])[] = [];
  if (r.rawData != null) out.push(r.rawData);
  if (r.rawExtendedData != null) out.push(r.rawExtendedData);
  if (r.rawExtraData != null) out.push(r.rawExtraData);
  return out;
}

interface ResultsTableProps<T> {
  columns: Column<T>[];
  data: T[];
  keyExtractor: (row: T) => string | number;
  emptyMessage: string;
  /** Column keys that can be clicked to sort. */
  sortableColumnKeys?: Set<string>;
  /** Key of the column that is currently the primary sort. */
  sortColumnKey?: string | null;
  /** Current sort direction for the primary sort column. */
  sortOrder?: 'asc' | 'desc' | null;
  /** Called when a sortable column header is clicked. */
  onColumnSort?: (columnKey: string) => void;
}

function ResultsTable<T>({
  columns,
  data,
  keyExtractor,
  emptyMessage,
  sortableColumnKeys,
  sortColumnKey,
  sortOrder,
  onColumnSort,
}: ResultsTableProps<T>) {
  if (data.length === 0) {
    return <p className="results-empty">{emptyMessage}</p>;
  }
  if (columns.length === 0) {
    return (
      <p className="results-empty">
        All columns are hidden. Use the Columns button above to show them.
      </p>
    );
  }
  const isSortable = (key: string) =>
    sortableColumnKeys?.has(key) && typeof onColumnSort === 'function';

  return (
    <div className="results-table-wrapper">
      <table className="results-table">
        <thead>
          <tr>
            {columns.map((col) => {
              const sortable = isSortable(col.key);
              const isSorted = sortColumnKey === col.key;
              return (
                <th key={col.key}>
                  {sortable ? (
                    <button
                      type="button"
                      className="results-table-sort-header"
                      onClick={() => onColumnSort?.(col.key)}
                      title={isSorted ? `Sorted ${sortOrder === 'desc' ? 'descending' : 'ascending'}. Click to reverse.` : `Sort by ${col.label}`}
                    >
                      <span className="results-table-sort-label">{col.label}</span>
                      {isSorted && (
                        <span className="results-table-sort-icon" aria-hidden>
                          {sortOrder === 'desc' ? ' ↓' : ' ↑'}
                        </span>
                      )}
                    </button>
                  ) : (
                    col.label
                  )}
                </th>
              );
            })}
          </tr>
        </thead>
        <tbody>
          {data.map((row, rowIndex) => {
            const key = keyExtractor(row);
            const rawPayloads = getRawPayloads(row);
            const rowStripe = rowIndex % 2 === 0 ? 'results-table-row-even' : 'results-table-row-odd';
            return (
              <Fragment key={key}>
                <tr className={rowStripe}>
                  {columns.map((col) => (
                    <td key={col.key}>{col.render(row)}</td>
                  ))}
                </tr>
                {rawPayloads.length > 0 && (
                  <tr className={`raw-data-row ${rowStripe}`} aria-hidden>
                    <td colSpan={columns.length} className="raw-data-cell">
                      <table className="raw-data-subtable">
                        <tbody>
                          {rawPayloads.map((payload, i) => (
                            <tr
                              key={i}
                              className={
                                i === 0
                                  ? 'raw-data-hex-row raw-data-hex-row-primary'
                                  : 'raw-data-hex-row raw-data-hex-row-secondary'
                              }
                            >
                              <td className="raw-data-hex-cell">
                                <span className="raw-data-hex">{rawPayloadToHex(payload)}</span>
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </td>
                  </tr>
                )}
              </Fragment>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

function formatValue(value: unknown): string {
  if (value == null) return '—';
  if (typeof value === 'boolean') return value ? 'Yes' : 'No';
  return String(value);
}

const GAME_RESULT_DISPLAY: Record<string, string> = {
  BLACK_WINS: '0-1',
  DRAW: '1/2-1/2',
  WHITE_WINS: '1-0',
  NOT_FINISHED: '*',
  WHITE_WINS_ON_FORFEIT: '+:-',
  DRAW_ON_FORFEIT: '=:=',
  BLACK_WINS_ON_FORFEIT: '-:+',
  BOTH_LOST: '0-0',
};

function formatGameResult(result: unknown): string {
  if (result == null) return '—';
  const s = String(result);
  return GAME_RESULT_DISPLAY[s] ?? s;
}

interface DateObject {
  year?: number;
  month?: number;
  day?: number;
}

function formatDate(date: unknown): string {
  if (date == null) return '—';
  if (typeof date === 'string') return date;
  const d = date as DateObject;
  const y = d.year ?? 0;
  const m = d.month ?? 0;
  const day = d.day ?? 0;
  if (y === 0) return '????.??.??';
  if (m === 0) return String(y);
  if (day === 0) return `${String(y).padStart(4, '0')}.${String(m).padStart(2, '0')}.??`;
  return `${String(y).padStart(4, '0')}-${String(m).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
}

function formatPlayerName(
  lastName?: string,
  firstName?: string,
  rating?: number
): string {
  const parts = [lastName, firstName].filter(Boolean);
  const name = parts.length ? parts.join(', ') : '—';
  if (rating != null && rating > 0) {
    return `${name} (${rating})`;
  }
  return name;
}

function getGameTagTitle(gt: GameDto['gameTag']): string {
  if (!gt) return '—';
  const titles = [
    gt.englishTitle,
    gt.germanTitle,
    (gt as Record<string, unknown>).frenchTitle,
    (gt as Record<string, unknown>).spanishTitle,
    (gt as Record<string, unknown>).italianTitle,
    (gt as Record<string, unknown>).dutchTitle,
    (gt as Record<string, unknown>).slovenianTitle,
    (gt as Record<string, unknown>).resTitle,
  ];
  const found = titles.find((t): t is string => typeof t === 'string');
  return found ?? '—';
}

export const GAME_COLUMNS: Column<GameDto>[] = [
  { key: 'id', label: 'ID', render: (g) => formatValue(g.id) },
  {
    key: 'white',
    label: 'White',
    render: (g) =>
      g.whitePlayer
        ? formatPlayerName(
            g.whitePlayer.lastName,
            g.whitePlayer.firstName,
            g.whiteElo
          )
        : '—',
  },
  {
    key: 'black',
    label: 'Black',
    render: (g) =>
      g.blackPlayer
        ? formatPlayerName(
            g.blackPlayer.lastName,
            g.blackPlayer.firstName,
            g.blackElo
          )
        : '—',
  },
  { key: 'result', label: 'Result', render: (g) => formatGameResult(g.result) },
  { key: 'date', label: 'Date', render: (g) => formatDate(g.date) },
  { key: 'eco', label: 'ECO', render: (g) => formatValue(g.eco) },
  { key: 'round', label: 'Round', render: (g) => formatValue(g.round) },
  {
    key: 'tournament',
    label: 'Tournament',
    render: (g) => formatValue(g.tournament?.title),
  },
  {
    key: 'source',
    label: 'Source',
    render: (g) => formatValue(g.source?.title),
  },
  {
    key: 'annotator',
    label: 'Annotator',
    render: (g) => formatValue(g.annotator?.name),
  },
  {
    key: 'gameTag',
    label: 'Game Tag',
    render: (g) => getGameTagTitle(g.gameTag),
  },
];

export const PLAYER_COLUMNS: Column<PlayerDto>[] = [
  { key: 'id', label: 'ID', render: (p) => formatValue(p.id) },
  { key: 'lastName', label: 'Last Name', render: (p) => formatValue(p.lastName) },
  { key: 'firstName', label: 'First Name', render: (p) => formatValue(p.firstName) },
  { key: 'gameCount', label: 'Game Count', render: (p) => formatValue(p.gameCount) },
];

export const TOURNAMENT_COLUMNS: Column<TournamentDto>[] = [
  { key: 'id', label: 'ID', render: (t) => formatValue(t.id) },
  { key: 'title', label: 'Title', render: (t) => formatValue(t.title) },
  { key: 'startDate', label: 'Start Date', render: (t) => formatDate(t.startDate) },
  { key: 'endDate', label: 'End Date', render: (t) => formatDate(t.endDate) },
  { key: 'place', label: 'Place', render: (t) => formatValue(t.place) },
  { key: 'country', label: 'Country', render: (t) => formatValue(t.country) },
  { key: 'category', label: 'Category', render: (t) => formatValue(t.category) },
  { key: 'rounds', label: 'Rounds', render: (t) => formatValue(t.rounds) },
  { key: 'type', label: 'Type', render: (t) => formatValue(t.type) },
  { key: 'timeControl', label: 'Time Control', render: (t) => formatValue(t.timeControl) },
  { key: 'complete', label: 'Complete', render: (t) => formatValue(t.complete) },
  { key: 'teamTournament', label: 'Team Tourn.', render: (t) => formatValue(t.teamTournament) },
  { key: 'gameCount', label: 'Game Count', render: (t) => formatValue(t.gameCount) },
];

export const ANNOTATOR_COLUMNS: Column<AnnotatorDto>[] = [
  { key: 'id', label: 'ID', render: (a) => formatValue(a.id) },
  { key: 'name', label: 'Name', render: (a) => formatValue(a.name) },
  { key: 'gameCount', label: 'Game Count', render: (a) => formatValue(a.gameCount) },
];

export const SOURCE_COLUMNS: Column<SourceDto>[] = [
  { key: 'id', label: 'ID', render: (s) => formatValue(s.id) },
  { key: 'title', label: 'Title', render: (s) => formatValue(s.title) },
  { key: 'publisher', label: 'Publisher', render: (s) => formatValue(s.publisher) },
  { key: 'publication', label: 'Publication', render: (s) => formatDate(s.publication) },
  { key: 'date', label: 'Date', render: (s) => formatDate(s.date) },
  { key: 'version', label: 'Version', render: (s) => formatValue(s.version) },
  { key: 'quality', label: 'Quality', render: (s) => formatValue(s.quality) },
  { key: 'gameCount', label: 'Game Count', render: (s) => formatValue(s.gameCount) },
];

export const TEAM_COLUMNS: Column<TeamDto>[] = [
  { key: 'id', label: 'ID', render: (t) => formatValue(t.id) },
  { key: 'title', label: 'Title', render: (t) => formatValue(t.title) },
  { key: 'teamNumber', label: 'Team #', render: (t) => formatValue(t.teamNumber) },
  { key: 'season', label: 'Season', render: (t) => formatValue(t.season) },
  { key: 'year', label: 'Year', render: (t) => formatValue(t.year) },
  { key: 'nation', label: 'Nation', render: (t) => formatValue(t.nation) },
  { key: 'gameCount', label: 'Game Count', render: (t) => formatValue(t.gameCount) },
];

export const GAMETAG_COLUMNS: Column<GameTagDto>[] = [
  { key: 'id', label: 'ID', render: (g) => formatValue(g.id) },
  { key: 'title', label: 'Title', render: (g) => formatValue(g.title) },
  { key: 'languages', label: 'Languages', render: (g) => formatValue(g.languages) },
  { key: 'languageCount', label: 'Lang Count', render: (g) => formatValue(g.languageCount) },
  { key: 'gameCount', label: 'Game Count', render: (g) => formatValue(g.gameCount) },
];

export default ResultsTable;

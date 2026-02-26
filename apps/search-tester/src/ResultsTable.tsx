import { Fragment, useCallback, useRef } from 'react';
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
  width: number;
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
  /** Custom column widths (overrides default column.width). */
  columnWidths?: Record<string, number>;
  /** Called when a column is resized via drag. */
  onColumnResize?: (key: string, width: number) => void;
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
  columnWidths = {},
  onColumnResize,
}: ResultsTableProps<T>) {
  const resizingRef = useRef<{ key: string; startX: number; startWidth: number } | null>(null);

  const handleResizeStart = useCallback(
    (e: React.MouseEvent, colKey: string) => {
      e.preventDefault();
      e.stopPropagation();
      const th = (e.target as HTMLElement).closest('th');
      if (!th) return;
      const startWidth = th.getBoundingClientRect().width;
      resizingRef.current = { key: colKey, startX: e.clientX, startWidth };

      const onMouseMove = (ev: MouseEvent) => {
        if (!resizingRef.current) return;
        const diff = ev.clientX - resizingRef.current.startX;
        const newWidth = Math.max(40, resizingRef.current.startWidth + diff);
        onColumnResize?.(resizingRef.current.key, newWidth);
      };
      const onMouseUp = () => {
        resizingRef.current = null;
        document.removeEventListener('mousemove', onMouseMove);
        document.removeEventListener('mouseup', onMouseUp);
      };
      document.addEventListener('mousemove', onMouseMove);
      document.addEventListener('mouseup', onMouseUp);
    },
    [onColumnResize]
  );

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

  const totalWidth = columns.reduce((sum, col) => sum + (columnWidths[col.key] ?? col.width), 0);

  return (
    <div className="results-table-wrapper">
      <table className="results-table" style={{ tableLayout: 'fixed', minWidth: totalWidth }}>
        <colgroup>
          {columns.map((col) => (
            <col key={col.key} style={{ width: columnWidths[col.key] ?? col.width }} />
          ))}
          <col className="results-table-filler-col" />
        </colgroup>
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
                  <div
                    className="results-table-resize-handle"
                    onMouseDown={(e) => handleResizeStart(e, col.key)}
                  />
                </th>
              );
            })}
            <th className="results-table-filler" />
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
                  <td className="results-table-filler" />
                </tr>
                {rawPayloads.length > 0 && (
                  <tr className={`raw-data-row ${rowStripe}`} aria-hidden>
                    <td colSpan={columns.length + 1} className="raw-data-cell">
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
  { key: 'id', label: 'ID', width: 55, render: (g) => formatValue(g.id) },
  {
    key: 'white',
    label: 'White',
    width: 180,
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
    width: 180,
    render: (g) =>
      g.blackPlayer
        ? formatPlayerName(
            g.blackPlayer.lastName,
            g.blackPlayer.firstName,
            g.blackElo
          )
        : '—',
  },
  { key: 'result', label: 'Result', width: 70, render: (g) => formatGameResult(g.result) },
  { key: 'date', label: 'Date', width: 100, render: (g) => formatDate(g.date) },
  { key: 'eco', label: 'ECO', width: 50, render: (g) => formatValue(g.eco) },
  { key: 'round', label: 'Round', width: 55, render: (g) => formatValue(g.round) },
  {
    key: 'tournament',
    label: 'Tournament',
    width: 180,
    render: (g) => formatValue(g.tournament?.title),
  },
  {
    key: 'source',
    label: 'Source',
    width: 150,
    render: (g) => formatValue(g.source?.title),
  },
  {
    key: 'annotator',
    label: 'Annotator',
    width: 120,
    render: (g) => formatValue(g.annotator?.name),
  },
  {
    key: 'gameTag',
    label: 'Game Tag',
    width: 150,
    render: (g) => getGameTagTitle(g.gameTag),
  },
];

export const PLAYER_COLUMNS: Column<PlayerDto>[] = [
  { key: 'id', label: 'ID', width: 55, render: (p) => formatValue(p.id) },
  { key: 'lastName', label: 'Last Name', width: 180, render: (p) => formatValue(p.lastName) },
  { key: 'firstName', label: 'First Name', width: 150, render: (p) => formatValue(p.firstName) },
  { key: 'gameCount', label: 'Game Count', width: 80, render: (p) => formatValue(p.gameCount) },
];

export const TOURNAMENT_COLUMNS: Column<TournamentDto>[] = [
  { key: 'id', label: 'ID', width: 55, render: (t) => formatValue(t.id) },
  { key: 'title', label: 'Title', width: 250, render: (t) => formatValue(t.title) },
  { key: 'place', label: 'Place', width: 150, render: (t) => formatValue(t.place) },
  { key: 'startDate', label: 'Start Date', width: 100, render: (t) => formatDate(t.startDate) },
  { key: 'typeCombined', label: 'Type', width: 100, render: (t) => formatValue(t.typeCombined) },
  { key: 'nation', label: 'Nation', width: 55, render: (t) => formatValue(t.nation) },
  { key: 'category', label: 'Category', width: 70, render: (t) => formatValue(t.categoryRoman) },
  { key: 'rounds', label: 'Rounds', width: 60, render: (t) => formatValue(t.rounds) },
  { key: 'gameCount', label: 'Game Count', width: 80, render: (t) => formatValue(t.gameCount) },
  { key: 'complete', label: 'Complete', width: 70, render: (t) => formatValue(t.complete) },
  {
    key: 'coordinates',
    label: 'Coordinates',
    width: 170,
    render: (t) => {
      if (t.latitude == null || t.longitude == null) return '';
      const lat = Math.abs(t.latitude).toFixed(4) + (t.latitude >= 0 ? ' N' : ' S');
      const lng = Math.abs(t.longitude).toFixed(4) + (t.longitude >= 0 ? ' E' : ' W');
      return `${lat}, ${lng}`;
    },
  },
  { key: 'endDate', label: 'End Date', width: 100, render: (t) => formatDate(t.endDate) },
  {
    key: 'tiebreak',
    label: 'Tiebreak',
    width: 180,
    render: (t) => (t.tiebreakRules && t.tiebreakRules.length > 0 ? t.tiebreakRules.join(', ') : ''),
  },
];

export const ANNOTATOR_COLUMNS: Column<AnnotatorDto>[] = [
  { key: 'id', label: 'ID', width: 55, render: (a) => formatValue(a.id) },
  { key: 'name', label: 'Name', width: 250, render: (a) => formatValue(a.name) },
  { key: 'gameCount', label: 'Game Count', width: 80, render: (a) => formatValue(a.gameCount) },
];

export const SOURCE_COLUMNS: Column<SourceDto>[] = [
  { key: 'id', label: 'ID', width: 55, render: (s) => formatValue(s.id) },
  { key: 'title', label: 'Title', width: 250, render: (s) => formatValue(s.title) },
  { key: 'publisher', label: 'Publisher', width: 150, render: (s) => formatValue(s.publisher) },
  { key: 'publication', label: 'Publication', width: 90, render: (s) => formatDate(s.publication) },
  { key: 'date', label: 'Date', width: 100, render: (s) => formatDate(s.date) },
  { key: 'version', label: 'Version', width: 60, render: (s) => formatValue(s.version) },
  { key: 'quality', label: 'Quality', width: 60, render: (s) => formatValue(s.quality) },
  { key: 'gameCount', label: 'Game Count', width: 80, render: (s) => formatValue(s.gameCount) },
];

export const TEAM_COLUMNS: Column<TeamDto>[] = [
  { key: 'id', label: 'ID', width: 55, render: (t) => formatValue(t.id) },
  { key: 'title', label: 'Title', width: 250, render: (t) => formatValue(t.title) },
  { key: 'teamNumber', label: 'Team #', width: 60, render: (t) => formatValue(t.teamNumber) },
  { key: 'season', label: 'Season', width: 80, render: (t) => formatValue(t.season) },
  { key: 'year', label: 'Year', width: 55, render: (t) => formatValue(t.year) },
  { key: 'nation', label: 'Nation', width: 60, render: (t) => formatValue(t.nation) },
  { key: 'gameCount', label: 'Game Count', width: 80, render: (t) => formatValue(t.gameCount) },
];

export const GAMETAG_COLUMNS: Column<GameTagDto>[] = [
  { key: 'id', label: 'ID', width: 55, render: (g) => formatValue(g.id) },
  { key: 'title', label: 'Title', width: 300, render: (g) => formatValue(g.title) },
  { key: 'languages', label: 'Languages', width: 150, render: (g) => formatValue(g.languages) },
  { key: 'languageCount', label: 'Lang Count', width: 80, render: (g) => formatValue(g.languageCount) },
  { key: 'gameCount', label: 'Game Count', width: 80, render: (g) => formatValue(g.gameCount) },
];

export default ResultsTable;

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

interface ResultsTableProps<T> {
  columns: Column<T>[];
  data: T[];
  keyExtractor: (row: T) => string | number;
  emptyMessage: string;
}

function ResultsTable<T>({
  columns,
  data,
  keyExtractor,
  emptyMessage,
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
  return (
    <div className="results-table-wrapper">
      <table className="results-table">
        <thead>
          <tr>
            {columns.map((col) => (
              <th key={col.key}>{col.label}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {data.map((row) => (
            <tr key={keyExtractor(row)}>
              {columns.map((col) => (
                <td key={col.key}>{col.render(row)}</td>
              ))}
            </tr>
          ))}
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
    render: (g) => formatValue(g.tournament?.name),
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
  { key: 'name', label: 'Name', render: (t) => formatValue(t.name) },
  { key: 'startDate', label: 'Start Date', render: (t) => formatDate(t.startDate) },
  { key: 'endDate', label: 'End Date', render: (t) => formatDate(t.endDate) },
  { key: 'site', label: 'Site', render: (t) => formatValue(t.site) },
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
  { key: 'englishTitle', label: 'English', render: (g) => formatValue(g.englishTitle) },
  { key: 'germanTitle', label: 'German', render: (g) => formatValue(g.germanTitle) },
  { key: 'frenchTitle', label: 'French', render: (g) => formatValue(g.frenchTitle) },
  { key: 'spanishTitle', label: 'Spanish', render: (g) => formatValue(g.spanishTitle) },
  { key: 'italianTitle', label: 'Italian', render: (g) => formatValue(g.italianTitle) },
  { key: 'dutchTitle', label: 'Dutch', render: (g) => formatValue(g.dutchTitle) },
  { key: 'slovenianTitle', label: 'Slovenian', render: (g) => formatValue(g.slovenianTitle) },
  { key: 'resTitle', label: 'Res Title', render: (g) => formatValue(g.resTitle) },
  { key: 'gameCount', label: 'Game Count', render: (g) => formatValue(g.gameCount) },
];

export default ResultsTable;

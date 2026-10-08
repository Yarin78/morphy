import { type ReactNode, useMemo, useState } from 'react';
import { Chess } from 'chess.js';
import { formatSan, splitPlayerName } from 'game-view';
import type { PositionMoveStats, PositionSummary } from '../../api/types';
import { useBoardView } from '../boardStore';
import { useSettings } from '../settings';
import { ColumnMenu } from './ColumnPicker';
import { MOVE_COLUMNS, type MoveColumnKey, resetMoveColumns, toggleMoveColumn, useMoveColumns } from './moveColumns';
import { formatCount, scoreOf, whiteToMove as whiteToMoveIn } from './positionStats';

// The players named for a move, of the strongest who played it
const PLAYERS_SHOWN = 3;

// Too few recent games say nothing of what's in fashion
const MIN_RECENT_GAMES = 10;

/** What the cells of a move are worked out from, besides the move. */
interface MoveContext {
  whiteToMove: boolean;
  /** The score of the side to move in all the games from the position, 0–1 */
  positionScore: number;
  /** The recent games and all the games that went on from the position; null if too few are recent */
  shares: { recent: number; all: number } | null;
}

/**
 * How much more a move is played of late than the other moves from the position, or less: its
 * share of the recent games against its share of all of them, as a power of two (1 is twice as
 * much, −1 half as much), between −1 and 1.
 */
function trendOf(move: PositionMoveStats, shares: { recent: number; all: number }): { trend: number; text: string } {
  const recentShare = move.recentGames / shares.recent;
  const share = move.games / shares.all;
  const trend = recentShare > 0 ? Math.max(-1, Math.min(1, Math.log2(recentShare / share))) : -1;
  const text = `${Math.round(100 * recentShare)}% of the recent games, ${Math.round(100 * share)}% of all`;
  return { trend, text };
}

// The colours of a move out of fashion, of one played as much as ever, and of one in fashion
const COLD = [159, 179, 200];
const STEADY = [210, 195, 163];
const HOT = [232, 89, 12];

/** The colour of a trend, from cold through steady to hot. */
function trendColour(trend: number): string {
  const [to, t] = trend < 0 ? [COLD, -trend] : [HOT, trend];
  return `rgb(${STEADY.map((c, i) => Math.round(c + (to[i] - c) * t)).join(',')})`;
}

/** A trend as a bar, the longer and the hotter in colour the more the move is played of late. */
function TrendBar({ move, shares }: { move: PositionMoveStats; shares: MoveContext['shares'] }) {
  if (!shares) return null;
  const { trend, text } = trendOf(move, shares);
  return (
    <div className="position-moves-trend-bar" title={text}>
      <span style={{ width: `${8 + ((trend + 1) / 2) * 92}%`, background: trendColour(trend) }} />
    </div>
  );
}

/** A difference in score, in percentage points: +3, −5. */
function formatDelta(points: number): string {
  return points > 0 ? `+${points}` : points < 0 ? `−${-points}` : '0';
}

interface MoveColumn {
  /** The header's explanation */
  title: (context: MoveContext, summary: PositionSummary) => string;
  className: string;
  render: (move: PositionMoveStats, context: MoveContext) => ReactNode;
  /** The cell's explanation */
  cellTitle?: (move: PositionMoveStats) => string;
}

const COLUMNS: Record<MoveColumnKey, MoveColumn> = {
  games: {
    title: (_, summary) => `${summary.games.toLocaleString()} games reached this position`,
    className: 'position-moves-num',
    render: (move) => formatCount(move.games),
    cellTitle: (move) => `${move.games.toLocaleString()} games`,
  },
  score: {
    title: ({ whiteToMove }) => `The points ${whiteToMove ? 'White' : 'Black'} got with the move`,
    className: 'position-moves-num',
    render: (move, { whiteToMove }) => `${Math.round(100 * scoreOf(move, whiteToMove))}%`,
    cellTitle: (move) => `White won ${move.whiteWins}, drawn ${move.draws}, Black won ${move.blackWins}`,
  },
  draws: {
    title: () => 'The games drawn',
    className: 'position-moves-num',
    render: (move) => `${move.games > 0 ? Math.round((100 * move.draws) / move.games) : 0}%`,
  },
  delta: {
    title: ({ whiteToMove }) =>
      `How much more ${whiteToMove ? 'White' : 'Black'} scored with the move than with all the moves, in percentage points`,
    className: 'position-moves-num',
    render: (move, { whiteToMove, positionScore }) => {
      const points = Math.round(100 * (scoreOf(move, whiteToMove) - positionScore));
      return (
        <span className={points > 0 ? 'position-moves-better' : points < 0 ? 'position-moves-worse' : undefined}>
          {formatDelta(points)}
        </span>
      );
    },
  },
  average: {
    title: ({ whiteToMove }) => `The average rating of the ${whiteToMove ? 'White' : 'Black'} players who played it`,
    className: 'position-moves-num',
    render: (move) => move.averageRating ?? '',
  },
  hot: {
    title: (_, summary) => `Whether the move is played more or less since ${summary.recentSince} than before`,
    className: 'position-moves-trend',
    render: (move, { shares }) => <TrendBar move={move} shares={shares} />,
  },
  last: {
    title: () => 'The year it was last played',
    className: 'position-moves-num',
    render: (move) => move.lastPlayed ?? '',
  },
  players: {
    title: () => 'Some of the highest rated players who played it',
    className: 'position-moves-players',
    render: (move) =>
      move.topPlayers
        .slice(0, PLAYERS_SHOWN)
        .map((p) => splitPlayerName(p.name).lastName)
        .join(', '),
    cellTitle: (move) =>
      move.topPlayers
        .slice(0, PLAYERS_SHOWN)
        .map((p) => (p.rating ? `${p.name} (${p.rating})` : p.name))
        .join('\n'),
  },
};

const LABELS = Object.fromEntries(MOVE_COLUMNS.map((c) => [c.key, c.label])) as Record<MoveColumnKey, string>;

/**
 * The moves played from the position on the board in the games of a reference database: how
 * often each was played, how it scored, whether it's in fashion, when it was last played and by
 * whom, in the columns picked by right-clicking their headers. A move clicked is played on the
 * board.
 */
export function PositionMoves({
  summary: shown,
  stale,
  error,
}: {
  /** Of the position on the board, or of the one before while it's searched for */
  summary: PositionSummary | null;
  /** Whether the summary is of the position before */
  stale: boolean;
  error: string | null;
}) {
  const view = useBoardView();
  const { moveNotation } = useSettings().notation;
  const columns = useMoveColumns();
  // The columns to pick from, where the column headers were right-clicked
  const [columnMenu, setColumnMenu] = useState<{ x: number; y: number } | null>(null);

  const context = useMemo<MoveContext | null>(() => {
    if (!shown) return null;
    const whiteToMove = whiteToMoveIn(shown.fen);
    const recent = shown.moves.reduce((sum, m) => sum + m.recentGames, 0);
    const all = shown.moves.reduce((sum, m) => sum + m.games, 0);
    return {
      whiteToMove,
      positionScore: scoreOf(shown, whiteToMove),
      shares: recent >= MIN_RECENT_GAMES ? { recent, all } : null,
    };
  }, [shown]);

  const play = (san: string) => {
    if (stale || !shown) return;
    try {
      const move = new Chess(shown.fen).move(san);
      view.playMove({ from: move.from, to: move.to, promotion: move.promotion });
    } catch {
      console.error('Not a legal move here:', san);
    }
  };

  return (
    <div className="position-moves">
      {error && <p className="position-moves-error">{error}</p>}
      {!error && !shown && <p className="position-moves-note">Looking up the position…</p>}
      {shown && shown.moves.length === 0 && (
        <p className="position-moves-note">No game in the database continued from this position.</p>
      )}
      {shown && context && shown.moves.length > 0 && (
        <table className={`position-moves-table${stale ? ' stale' : ''}`}>
          <thead>
            <tr
              onContextMenu={(e) => {
                e.preventDefault();
                setColumnMenu({ x: e.clientX, y: e.clientY });
              }}
            >
              <th className="position-moves-move">Move</th>
              {columns.shown.map((key) => (
                <th key={key} className={COLUMNS[key].className} title={COLUMNS[key].title(context, shown)}>
                  {LABELS[key]}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {shown.moves.map((move) => (
              <tr key={move.san} onClick={() => play(move.san)}>
                <td className="position-moves-move">{formatSan(move.san, moveNotation)}</td>
                {columns.shown.map((key) => (
                  <td key={key} className={COLUMNS[key].className} title={COLUMNS[key].cellTitle?.(move)}>
                    {COLUMNS[key].render(move, context)}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      )}
      {columnMenu && (
        <ColumnMenu
          {...columnMenu}
          columns={{
            all: [...MOVE_COLUMNS],
            shown: columns.shown.map((key) => ({ key })),
            isDefault: columns.isDefault,
          }}
          onToggle={toggleMoveColumn}
          onReset={resetMoveColumns}
          resetLabel="Default columns"
          onClose={() => setColumnMenu(null)}
        />
      )}
    </div>
  );
}

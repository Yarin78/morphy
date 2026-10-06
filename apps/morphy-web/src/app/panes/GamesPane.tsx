import { type PointerEvent, type ReactNode, type RefObject, useEffect, useMemo, useRef, useState } from 'react';
import { TbLayoutColumns, TbLayoutRows } from 'react-icons/tb';
import { fetchReferenceDatabases } from '../../api/client';
import type { DatabaseResponse } from '../../api/types';
import { usePositionSearch } from '../../search/usePositionSearch';
import { useBoardView } from '../boardStore';
import { useDocuments } from '../documentsStore';
import { formatCount, scoreOf, whiteToMove } from './positionStats';
import { PositionMoves } from './PositionMoves';
import { ResultsList } from './SearchPane';

// The moves beside the games, or above them; the same in every board
const ARRANGEMENT_KEY = 'morphy-games-pane-arrangement';
type Arrangement = 'side' | 'stacked';

// The share of the pane's width the moves take beside the games, or of its height above them;
// the same in every board
const SPLIT_KEYS: Record<Arrangement, string> = {
  side: 'morphy-games-pane-split',
  stacked: 'morphy-games-pane-split-stacked',
};
const DEFAULT_SPLIT = 0.45;
const MIN_SPLIT = 0.2;
const MAX_SPLIT = 0.8;

// The reference database picked last, which a board opened later starts with
const REFERENCE_KEY = 'morphy-reference-database';

type ReferenceDatabase = DatabaseResponse & { referenceName: string };

function load(key: string): string | null {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}

function save(key: string, value: string) {
  try {
    localStorage.setItem(key, value);
  } catch {
    // not kept, then
  }
}

function loadSplit(arrangement: Arrangement): number {
  const saved = Number(load(SPLIT_KEYS[arrangement]));
  return saved >= MIN_SPLIT && saved <= MAX_SPLIT ? saved : DEFAULT_SPLIT;
}

/** The reference databases, as pills, the one searched picked. */
function DatabasePills({
  databases,
  picked,
  onPick,
}: {
  databases: ReferenceDatabase[];
  picked: string;
  onPick: (id: string) => void;
}) {
  return (
    <div className="games-pane-databases" role="radiogroup" aria-label="Reference database">
      {databases.map((db) => (
        <button
          key={db.id}
          role="radio"
          aria-checked={db.id === picked}
          className={`games-pane-database${db.id === picked ? ' picked' : ''}`}
          onClick={() => onPick(db.id)}
          title={db.displayName}
        >
          {db.referenceName}
        </button>
      ))}
    </div>
  );
}

/** The line between the moves and the games, dragged to share the width (or height) differently. */
function useSplit(paneRef: RefObject<HTMLDivElement | null>, arrangement: Arrangement) {
  const [splits, setSplits] = useState(() => ({ side: loadSplit('side'), stacked: loadSplit('stacked') }));
  const split = splits[arrangement];
  const startResize = (e: PointerEvent<HTMLDivElement>) => {
    const pane = paneRef.current;
    if (!pane) return;
    e.preventDefault();
    const handle = e.currentTarget;
    handle.setPointerCapture(e.pointerId);
    const bounds = pane.getBoundingClientRect();
    let latest = split;
    const onMove = (ev: globalThis.PointerEvent) => {
      const share =
        arrangement === 'side'
          ? (ev.clientX - bounds.left) / bounds.width
          : (ev.clientY - bounds.top) / bounds.height;
      latest = Math.max(MIN_SPLIT, Math.min(MAX_SPLIT, share));
      setSplits((all) => ({ ...all, [arrangement]: latest }));
    };
    const onUp = () => {
      save(SPLIT_KEYS[arrangement], String(latest));
      handle.removeEventListener('pointermove', onMove);
      handle.removeEventListener('pointerup', onUp);
      handle.removeEventListener('pointercancel', onUp);
    };
    handle.addEventListener('pointermove', onMove);
    handle.addEventListener('pointerup', onUp);
    handle.addEventListener('pointercancel', onUp);
  };
  return { split, startResize };
}

/** The games of a reference database from a position: the totals, the moves and the games. */
function PositionGames({ databaseId, fen, pills }: { databaseId: string; fen: string; pills: ReactNode }) {
  const { dispatch } = useDocuments();
  const { search, summary } = usePositionSearch(databaseId, fen);
  const [arrangement, setArrangement] = useState<Arrangement>(() =>
    load(ARRANGEMENT_KEY) === 'stacked' ? 'stacked' : 'side'
  );
  const rearrange = () => {
    const next = arrangement === 'side' ? 'stacked' : 'side';
    setArrangement(next);
    save(ARRANGEMENT_KEY, next);
  };
  const splitRef = useRef<HTMLDivElement>(null);
  const { split, startResize } = useSplit(splitRef, arrangement);

  // Until the position on the board has been searched for, what was played from the one before
  // is shown
  const shown = summary?.kind === 'loaded' && summary.databaseId === databaseId ? summary.summary : null;
  const stale = shown?.fen !== fen;
  const error =
    summary?.kind === 'error' && summary.databaseId === databaseId && summary.fen === fen ? summary.message : null;
  const white = shown ? whiteToMove(shown.fen) : true;

  return (
    <div className="games-pane">
      <div className="games-pane-head">
        <span className="games-pane-totals">
          {shown && (
            <>
              <span title={`${shown.games.toLocaleString()} games reached this position`}>
                {formatCount(shown.games)} {shown.games === 1 ? 'game' : 'games'}
              </span>
              {shown.games > 0 && (
                <span
                  title={`White won ${shown.whiteWins.toLocaleString()}, drawn ${shown.draws.toLocaleString()}, Black won ${shown.blackWins.toLocaleString()}`}
                >
                  {' · '}
                  {white ? 'White' : 'Black'} scores {Math.round(100 * scoreOf(shown, white))}%
                </span>
              )}
            </>
          )}
        </span>
        <div className="games-pane-head-right">
          {pills}
          <button
            type="button"
            className="engine-icon-button"
            onClick={rearrange}
            title={arrangement === 'side' ? 'Show the moves above the games' : 'Show the moves beside the games'}
            aria-label={arrangement === 'side' ? 'Show the moves above the games' : 'Show the moves beside the games'}
          >
            {arrangement === 'side' ? <TbLayoutRows /> : <TbLayoutColumns />}
          </button>
        </div>
      </div>
      <div className={`games-pane-split ${arrangement}`} ref={splitRef}>
        <div className="games-pane-moves" style={{ flexBasis: `${split * 100}%` }}>
          <PositionMoves summary={shown} stale={stale} error={error} />
        </div>
        <div className="games-pane-divider" onPointerDown={startResize} />
        <div className="games-pane-games search-pane">
          <ResultsList
            databaseId={databaseId}
            search={search}
            // The game opens at the position, the first time it's reached in it (index 1 isn't
            // known to be it, so the position is looked for)
            openGame={(gameId) => dispatch({ type: 'openBoard', databaseId, gameId, quote: { fen, index: 1 } })}
            previewKeys={() => false}
            // The count is in the pane's top row
            countRow={false}
            columnSet="positionGames"
          />
        </div>
      </div>
    </div>
  );
}

/**
 * The games from the position on the board, below it, in a reference database picked from those
 * there are: how many reached it and how they went, the moves played from it on the left, and the
 * games on the right.
 */
export function GamesPane() {
  const { game, version } = useBoardView();
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const fen = useMemo(() => game.fen(), [game, version]);

  const [databases, setDatabases] = useState<ReferenceDatabase[] | { error: string } | null>(null);
  useEffect(() => {
    fetchReferenceDatabases()
      .then(setDatabases)
      .catch((err: unknown) => setDatabases({ error: err instanceof Error ? err.message : String(err) }));
  }, []);

  const [picked, setPicked] = useState(() => load(REFERENCE_KEY));
  const pick = (id: string) => {
    setPicked(id);
    save(REFERENCE_KEY, id);
  };

  if (!databases) return <div className="games-pane-note">Loading the reference databases…</div>;
  if ('error' in databases) return <div className="games-pane-note search-error">{databases.error}</div>;
  if (databases.length === 0) {
    return <div className="games-pane-note">There is no reference database to look in.</div>;
  }
  const databaseId = databases.some((db) => db.id === picked) ? picked! : databases[0].id;
  return (
    <PositionGames
      databaseId={databaseId}
      fen={fen}
      pills={<DatabasePills databases={databases} picked={databaseId} onPick={pick} />}
    />
  );
}

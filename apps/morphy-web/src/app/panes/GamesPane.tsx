import { type PointerEvent, type ReactNode, type RefObject, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { TbLayoutColumns, TbLayoutRows } from 'react-icons/tb';
import { buildPositionIndex, fetchPositionIndex, fetchPositionIndexes } from '../../api/client';
import type { PositionIndexResponse, PositionIndexState } from '../../api/types';
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

// The position index picked last, which a board opened later starts with
const INDEX_KEY = 'morphy-position-index';

// How often the status of an index being built is asked for
const BUILD_POLL_MS = 2000;

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

/** What an index holds, for its pill's tooltip. */
function describe(index: PositionIndexResponse): string {
  const games = index.games ? `${index.games.toLocaleString()} games` : 'The games';
  return index.filter ? `${games} of ${index.databaseId} matching ${index.filter}` : `${games} of ${index.databaseId}`;
}

/** The position indexes, as pills, the one searched picked. */
function IndexPills({
  indexes,
  picked,
  onPick,
}: {
  indexes: PositionIndexResponse[];
  picked: string;
  onPick: (id: string) => void;
}) {
  return (
    <div className="games-pane-databases" role="radiogroup" aria-label="Position index">
      {indexes.map((index) => (
        <button
          key={index.id}
          role="radio"
          aria-checked={index.id === picked}
          className={`games-pane-database${index.id === picked ? ' picked' : ''}`}
          onClick={() => onPick(index.id)}
          title={describe(index)}
        >
          {index.name}
        </button>
      ))}
    </div>
  );
}

/**
 * Why the games of a position may be slow to come or incomplete, in the pane's top row: there's
 * no index, so every game was played through, or it's out of date, with how many games it lacks;
 * and a way to build it. While it's built, how that goes, until it's done.
 */
function IndexNote({
  listed,
  state,
  durationMs,
  onBuilt,
}: {
  /** The index as listed, which this starts from */
  listed: PositionIndexResponse;
  /** How the search of the position shown used it, once answered */
  state: PositionIndexState | null;
  durationMs: number | null;
  onBuilt: () => void;
}) {
  const [status, setStatus] = useState(listed);
  const [error, setError] = useState<string | null>(null);

  // While it's built, its status is asked for every few seconds
  useEffect(() => {
    if (status.status !== 'building') return;
    const timer = setTimeout(() => {
      fetchPositionIndex(status.id)
        .then((next) => (next.status === 'building' ? setStatus(next) : onBuilt()))
        .catch((err: unknown) => setError(err instanceof Error ? err.message : String(err)));
    }, BUILD_POLL_MS);
    return () => clearTimeout(timer);
  }, [status, onBuilt]);

  const build = () => {
    setError(null);
    buildPositionIndex(status.id)
      .then(setStatus)
      .catch((err: unknown) => setError(err instanceof Error ? err.message : String(err)));
  };

  if (error) return <span className="games-pane-index-note search-error">{error}</span>;
  if (status.status === 'building') {
    return (
      <span className="games-pane-index-note" title={status.message}>
        Building the index…{status.message ? ` ${status.message}` : ''}
      </span>
    );
  }
  const seconds = durationMs != null ? ` in ${(durationMs / 1000).toFixed(1)} s` : '';
  let note: string;
  let title: string;
  if (state?.status === 'missing') {
    note = 'No index';
    title = `${state.message ?? 'There is no index'}, so every game was played through to find the position${seconds}`;
  } else if (state?.status === 'stale') {
    const missing = state.missingGames ?? 0;
    note =
      missing > 0
        ? `Index out of date · ${formatCount(missing)} ${missing === 1 ? 'game' : 'games'} missing`
        : 'Index out of date';
    title =
      missing > 0
        ? `${missing.toLocaleString()} games have been added to ${status.databaseId} since the index was built` +
          (status.filter ? ' (not all of them may match its filter)' : '') +
          ', so they are not among these'
        : `Games of ${status.databaseId} have been changed or deleted since the index was built; the moves and games shown may be a little off`;
  } else if (state == null && status.status === 'failed') {
    note = 'The last build failed';
    title = status.message ?? note;
  } else {
    return null;
  }
  return (
    <span className="games-pane-index-note" title={title}>
      {note}
      {' · '}
      <button type="button" className="games-pane-index-build" onClick={build} title={describe(status)}>
        {state?.status === 'stale' || status.status === 'failed' ? 'Rebuild' : 'Build'}
      </button>
    </span>
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

/** The games of a position index from a position: the totals, the moves and the games. */
function PositionGames({
  index,
  fen,
  pills,
  onBuilt,
}: {
  index: PositionIndexResponse;
  fen: string;
  pills: ReactNode;
  onBuilt: () => void;
}) {
  const { dispatch } = useDocuments();
  const indexId = index.id;
  const databaseId = index.databaseId;
  const { search, summary } = usePositionSearch(indexId, fen, `${index.status} ${index.builtAt ?? ''}`);
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
  const loaded = summary?.kind === 'loaded' && summary.indexId === indexId ? summary : null;
  const shown = loaded?.summary ?? null;
  const stale = shown?.fen !== fen;
  const error =
    summary?.kind === 'error' && summary.indexId === indexId && summary.fen === fen ? summary.message : null;
  const white = shown ? whiteToMove(shown.fen) : true;

  return (
    <div className="games-pane">
      <div className="games-pane-head">
        <div className="games-pane-head-left">
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
          <IndexNote
            // Its own state for each index, and again when it has been built
            key={`${index.id} ${index.status} ${index.builtAt ?? ''}`}
            listed={index}
            state={loaded?.index ?? null}
            durationMs={loaded?.durationMs ?? null}
            onBuilt={onBuilt}
          />
        </div>
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
 * The games from the position on the board, below it, in a position index picked from those the
 * service defines: how many reached it and how they went, the moves played from it on the left, and
 * the games on the right. Without an index every game is played through instead, which takes longer;
 * an index that isn't built, or is out of date, can be built from here.
 */
export function GamesPane() {
  const { game, version } = useBoardView();
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const fen = useMemo(() => game.fen(), [game, version]);

  const [indexes, setIndexes] = useState<PositionIndexResponse[] | { error: string } | null>(null);
  const reload = useCallback(() => {
    fetchPositionIndexes()
      .then(setIndexes)
      .catch((err: unknown) => setIndexes({ error: err instanceof Error ? err.message : String(err) }));
  }, []);
  useEffect(reload, [reload]);

  const [picked, setPicked] = useState(() => load(INDEX_KEY));
  const pick = (id: string) => {
    setPicked(id);
    save(INDEX_KEY, id);
  };

  if (!indexes) return <div className="games-pane-note">Loading the position indexes…</div>;
  if ('error' in indexes) return <div className="games-pane-note search-error">{indexes.error}</div>;
  if (indexes.length === 0) {
    return (
      <div className="games-pane-note">
        No position index is defined; the service reads them from position-indexes.json.
      </div>
    );
  }
  const index = indexes.find((i) => i.id === picked) ?? indexes[0];
  const pills = <IndexPills indexes={indexes} picked={index.id} onPick={pick} />;
  return <PositionGames index={index} fen={fen} pills={pills} onBuilt={reload} />;
}

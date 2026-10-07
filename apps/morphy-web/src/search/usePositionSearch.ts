import { useCallback, useEffect, useRef, useState } from 'react';
import { searchPosition } from '../api/client';
import type { PositionSummary } from '../api/types';
import { shownColumnKeys } from './columnLayout';
import { NOTATION_COLUMN } from './columns';
import type { ResultsSearch, SearchResults, SortOrder } from './useDatabaseSearch';

// The search of a position index's games by position: what was played from the position, and the
// games that reached it, fetched a page at a time as they're scrolled to, as a database's game
// search lists them. A new position is searched for at once.

const PAGE_SIZE = 100;

/** What was played from a position, or why it isn't known. */
export type SummaryState =
  | { kind: 'loaded'; indexId: string; summary: PositionSummary }
  | { kind: 'error'; indexId: string; fen: string; message: string };

interface State {
  results: SearchResults | null;
  selected: number | null;
  sort: SortOrder;
  /** Of the last position answered, until the next one is */
  summary: SummaryState | null;
}

const INITIAL: State = { results: null, selected: null, sort: { field: 'id', order: 'asc' }, summary: null };

export interface PositionSearch {
  search: ResultsSearch;
  summary: SummaryState | null;
}

export function usePositionSearch(indexId: string, fen: string): PositionSearch {
  const [state, setState] = useState<State>(INITIAL);
  // The latest state, changed at once, so a fetch started right after a change sees it
  const stateRef = useRef(state);
  // The latest fetch; an answer to an earlier one is dropped
  const fetchId = useRef(0);

  const update = useCallback((change: (s: State) => State) => {
    stateRef.current = change(stateRef.current);
    setState(stateRef.current);
  }, []);

  const fetchPage = useCallback(
    async (offset: number) => {
      const id = ++fetchId.current;
      const first = offset === 0;
      const { sort } = stateRef.current;
      const sortBy = `${sort.order === 'desc' ? '-' : '+'}${sort.field}`;
      update((s) => ({
        ...s,
        results: {
          rows: first ? [] : (s.results?.rows ?? []),
          total: first ? null : (s.results?.total ?? null),
          complete: false,
          loading: true,
          error: null,
          durationMs: first ? null : (s.results?.durationMs ?? null),
          query: fen,
          sortBy,
        },
        selected: first ? null : s.selected,
      }));
      try {
        const res = await searchPosition(indexId, {
          fen,
          sortBy,
          offset,
          limit: PAGE_SIZE,
          // The first moves of the games come with the moves, which are only fetched when shown
          includeMoves: shownColumnKeys('positionGames').includes(NOTATION_COLUMN) || undefined,
        });
        if (fetchId.current !== id) return;
        const rows = res.games.games as { id: number }[];
        update((s) => {
          const all = [...(first ? [] : (s.results?.rows ?? [])), ...rows];
          return {
            ...s,
            results: {
              rows: all,
              total: res.games.totalCount,
              complete: rows.length < PAGE_SIZE || (res.games.totalCount != null && all.length >= res.games.totalCount),
              loading: false,
              error: null,
              durationMs: first ? res.games.metadata.executionTimeMs : (s.results?.durationMs ?? null),
              query: fen,
              sortBy,
            },
            summary: res.summary ? { kind: 'loaded', indexId, summary: res.summary } : s.summary,
          };
        });
      } catch (err) {
        if (fetchId.current !== id) return;
        const message = err instanceof Error ? err.message : String(err);
        update((s) => ({
          ...s,
          results: {
            rows: s.results?.rows ?? [],
            total: null,
            complete: true,
            loading: false,
            error: message,
            durationMs: s.results?.durationMs ?? null,
            query: fen,
            sortBy,
          },
          summary: first ? { kind: 'error', indexId, fen, message } : s.summary,
        }));
      }
    },
    [indexId, fen, update]
  );

  useEffect(() => {
    void fetchPage(0);
  }, [fetchPage]);

  return {
    search: {
      kind: 'games',
      current: state,
      run: () => void fetchPage(0),
      loadMore: () => {
        const r = state.results;
        if (r && !r.loading && !r.complete && !r.error) void fetchPage(r.rows.length);
      },
      sortBy: (field, defaultOrder) => {
        const sort = stateRef.current.sort;
        const order = sort.field === field ? (sort.order === 'asc' ? 'desc' : 'asc') : defaultOrder;
        update((s) => ({ ...s, sort: { field, order } }));
        void fetchPage(0);
      },
      select: (index) => update((s) => ({ ...s, selected: index })),
    },
    summary: state.summary,
  };
}

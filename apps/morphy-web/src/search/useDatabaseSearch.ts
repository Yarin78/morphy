import { useCallback, useEffect, useRef, useState } from 'react';
import { search } from '../api/client';
import type { EntitySearchResponse, GameSearchResponse } from '../api/types';
import { shownColumnKeys } from './columnLayout';
import { NOTATION_COLUMN } from './columns';
import {
  EMPTY_ENTITY_FORM,
  EMPTY_GAME_FORM,
  ENTITY_ID_FIELDS,
  type EntityForm,
  entityQuery,
  type GameForm,
  gameQuery,
  SEARCH_KINDS,
  type SearchKind,
} from './queries';

// The searches of a database document: one per kind of thing searched for, each with its form,
// or a query typed in full, and its results, fetched a page at a time as they're scrolled to.

const PAGE_SIZE = 100;

// How long after the form was last changed its search is run, so typing doesn't search each letter
const AUTO_SEARCH_DELAY_MS = 300;

export interface SortOrder {
  /** The sort field of the search language, e.g. playedDate */
  field: string;
  order: 'asc' | 'desc';
}

export interface SearchResults {
  rows: { id: number }[];
  /** All the matches, when the service counted them */
  total: number | null;
  /** Whether every match has been fetched */
  complete: boolean;
  loading: boolean;
  error: string | null;
  /** How long the service took to search, in milliseconds; null until it answers */
  durationMs: number | null;
  /** The filter query sent, empty for everything */
  query: string;
  /** The sort sent, e.g. +id */
  sortBy: string;
}

export interface KindSearch {
  /** Searching by the form, or by a query typed in full */
  mode: 'form' | 'query';
  advancedOpen: boolean;
  gameForm: GameForm;
  entityForm: EntityForm;
  queryText: string;
  /** The order of the results; null for the kind's own (the entities') */
  sort: SortOrder | null;
  /** The results of the last search; null before the first */
  results: SearchResults | null;
  /** The index of the row picked in the results */
  selected: number | null;
}

const EMPTY_SEARCH: KindSearch = {
  mode: 'form',
  advancedOpen: false,
  gameForm: EMPTY_GAME_FORM,
  entityForm: EMPTY_ENTITY_FORM,
  queryText: '',
  sort: null,
  results: null,
  selected: null,
};

type Searches = Record<SearchKind, KindSearch>;

// Games are in the order of their numbers, shown as sorted by it, so the first click reverses it
const INITIAL: Searches = {
  ...(Object.fromEntries(SEARCH_KINDS.map((k) => [k, EMPTY_SEARCH])) as Searches),
  games: { ...EMPTY_SEARCH, sort: { field: 'id', order: 'asc' } },
};

/** The query a search sends: the form's, or the one typed. */
export function queryOf(kind: SearchKind, s: KindSearch): string {
  if (s.mode === 'query') return s.queryText.trim();
  return kind === 'games' ? gameQuery(s.gameForm) : entityQuery(kind, s.entityForm);
}

function sortParam(sort: SortOrder | null): string {
  if (!sort) return 'default';
  return `${sort.order === 'desc' ? '-' : '+'}${sort.field}`;
}

export interface DatabaseSearch {
  kind: SearchKind;
  setKind: (kind: SearchKind) => void;
  current: KindSearch;
  /** Changes the search of the kind shown */
  update: (changes: Partial<KindSearch>) => void;
  updateGameForm: (changes: Partial<GameForm>) => void;
  updateEntityForm: (changes: Partial<EntityForm>) => void;
  /** Searches again from the first page, the kind shown or another */
  run: (kind?: SearchKind) => void;
  /** Fetches the next page of results */
  loadMore: () => void;
  /** Clears the form and the query, and searches for everything */
  reset: () => void;
  sortBy: (field: string, defaultOrder: 'asc' | 'desc') => void;
  select: (index: number | null) => void;
  /** Shows the games of an entity found by a search */
  showGamesOf: (kind: Exclude<SearchKind, 'games'>, id: number, label: string) => void;
}

export function useDatabaseSearch(databaseId: string): DatabaseSearch {
  const [kind, setKindState] = useState<SearchKind>('games');
  const [searches, setSearches] = useState<Searches>(INITIAL);
  // The latest searches, changed at once, so a fetch started right after a change sees it
  const searchesRef = useRef(searches);
  // The latest fetch of each kind; an answer to an earlier one is dropped
  const fetchIds = useRef<Record<string, number>>({});

  const updateKind = useCallback((k: SearchKind, change: (s: KindSearch) => KindSearch) => {
    const next = { ...searchesRef.current, [k]: change(searchesRef.current[k]) };
    searchesRef.current = next;
    setSearches(next);
  }, []);

  const fetchPage = useCallback(
    async (k: SearchKind, offset: number) => {
      const s = searchesRef.current[k];
      const fetchId = (fetchIds.current[k] ?? 0) + 1;
      fetchIds.current[k] = fetchId;
      const first = offset === 0;
      const filter = queryOf(k, s);
      const sortBy = sortParam(s.sort);
      updateKind(k, (prev) => ({
        ...prev,
        results: {
          rows: first ? [] : (prev.results?.rows ?? []),
          total: first ? null : (prev.results?.total ?? null),
          complete: false,
          loading: true,
          error: null,
          durationMs: first ? null : (prev.results?.durationMs ?? null),
          query: filter,
          sortBy,
        },
        selected: first ? null : prev.selected,
      }));
      const request = {
        filter: filter || undefined,
        sortBy,
        offset,
        limit: PAGE_SIZE,
        // The first moves of the games come with the moves, which are only fetched when shown
        ...(k === 'games' && shownColumnKeys('games').includes(NOTATION_COLUMN) ? { includeMoves: true } : {}),
      };
      try {
        const res = await search<GameSearchResponse | EntitySearchResponse<{ id: number }>>(databaseId, k, request);
        if (fetchIds.current[k] !== fetchId) return;
        const rows = ('games' in res ? res.games : res.items) as { id: number }[];
        updateKind(k, (prev) => {
          const all = [...(first ? [] : (prev.results?.rows ?? [])), ...rows];
          return {
            ...prev,
            results: {
              rows: all,
              total: res.totalCount,
              complete: rows.length < PAGE_SIZE || (res.totalCount != null && all.length >= res.totalCount),
              loading: false,
              error: null,
              // The time of the search, its first page; the later pages are continuations
              durationMs: first ? res.metadata.executionTimeMs : (prev.results?.durationMs ?? null),
              query: filter,
              sortBy,
            },
            // The first match is shown, so the preview has something in it
            selected: first ? (all.length > 0 ? 0 : null) : prev.selected,
          };
        });
      } catch (err) {
        if (fetchIds.current[k] !== fetchId) return;
        const message = err instanceof Error ? err.message : String(err);
        updateKind(k, (prev) => ({
          ...prev,
          results: {
            rows: prev.results?.rows ?? [],
            total: null,
            complete: true,
            loading: false,
            error: message,
            durationMs: prev.results?.durationMs ?? null,
            query: filter,
            sortBy,
          },
        }));
      }
    },
    [databaseId, updateKind]
  );

  const run = useCallback((k?: SearchKind) => void fetchPage(k ?? kind, 0), [fetchPage, kind]);

  // Every game is listed when the database is opened
  useEffect(() => {
    void fetchPage('games', 0);
  }, [fetchPage]);

  const setKind = useCallback(
    (k: SearchKind) => {
      setKindState(k);
      // A kind is searched the first time it's shown, for everything
      if (!searchesRef.current[k].results) void fetchPage(k, 0);
    },
    [fetchPage]
  );

  const current = searches[kind];

  // The form searches as it's changed: once its query is other than the last one searched for. A
  // query typed in full waits for Enter, as it's often not one until finished.
  const formQuery = current.mode === 'form' ? queryOf(kind, current) : null;
  useEffect(() => {
    if (formQuery == null) return;
    const results = searchesRef.current[kind].results;
    // Not yet searched: the kind is searched when first shown
    if (!results || results.query === formQuery) return;
    const timer = setTimeout(() => void fetchPage(kind, 0), AUTO_SEARCH_DELAY_MS);
    return () => clearTimeout(timer);
  }, [kind, formQuery, fetchPage]);

  return {
    kind,
    setKind,
    current,
    update: (changes) => updateKind(kind, (s) => ({ ...s, ...changes })),
    updateGameForm: (changes) => updateKind(kind, (s) => ({ ...s, gameForm: { ...s.gameForm, ...changes } })),
    updateEntityForm: (changes) => updateKind(kind, (s) => ({ ...s, entityForm: { ...s.entityForm, ...changes } })),
    run,
    loadMore: () => {
      const r = current.results;
      if (r && !r.loading && !r.complete && !r.error) void fetchPage(kind, r.rows.length);
    },
    reset: () => {
      updateKind(kind, (s) => ({ ...EMPTY_SEARCH, advancedOpen: s.advancedOpen, sort: s.sort, results: s.results }));
      void fetchPage(kind, 0);
    },
    sortBy: (field, defaultOrder) => {
      const sort = current.sort;
      const order = sort?.field === field ? (sort.order === 'asc' ? 'desc' : 'asc') : defaultOrder;
      updateKind(kind, (s) => ({ ...s, sort: { field, order } }));
      void fetchPage(kind, 0);
    },
    select: (index) => updateKind(kind, (s) => ({ ...s, selected: index })),
    showGamesOf: (entityKind, id, label) => {
      updateKind('games', (s) => ({
        ...s,
        mode: 'form',
        advancedOpen: true,
        gameForm: { ...EMPTY_GAME_FORM, entity: { field: ENTITY_ID_FIELDS[entityKind], id, label } },
      }));
      setKindState('games');
      void fetchPage('games', 0);
    },
  };
}

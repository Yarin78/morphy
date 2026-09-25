import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { CollapsiblePanel, ShowPanelButton } from './CollapsiblePanel';
import { QueryPlanVisualiser } from './QueryPlanVisualiser';
import { ResultsSection } from './ResultsSection';
import { SearchPanel } from './SearchPanel';
import { fetchDatabases, fetchFilterOptions } from './api/client';
import type {
  EntitySearchRequest,
  FilterOptionsResponse,
  GameSearchRequest,
  QueryPlanDebugInfo,
  RawRecord,
} from './api/types';
import type { EntityType } from './entityConfig';
import type { SavedSearch } from './savedSearchTypes';
import {
  ENTITY_CONFIG,
  getDefaultSortDirection,
  getSortOptions,
  runSearch,
  SORTABLE_COLUMN_MAP,
} from './entityConfig';
import { useLocalStorage } from './hooks/useLocalStorage';
import './App.css';

const COLUMN_VISIBILITY_KEY = 'search-tester-hidden-columns';
const QUERY_PLAN_PANEL_KEY = 'search-tester-show-query-plan';
const REQUEST_RESPONSE_PANEL_KEY = 'search-tester-show-request-response';
const SAVED_SEARCHES_KEY = 'search-tester-saved-searches';

function loadSavedSearches(): SavedSearch[] {
  try {
    const raw = localStorage.getItem(SAVED_SEARCHES_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

function persistSavedSearches(searches: SavedSearch[]) {
  try {
    localStorage.setItem(SAVED_SEARCHES_KEY, JSON.stringify(searches));
  } catch {
    // ignore
  }
}

function loadHiddenColumns(): Record<string, string[]> {
  try {
    const raw = localStorage.getItem(COLUMN_VISIBILITY_KEY);
    return raw ? JSON.parse(raw) : {};
  } catch {
    return {};
  }
}

function saveHiddenColumns(data: Record<string, string[]>) {
  try {
    localStorage.setItem(COLUMN_VISIBILITY_KEY, JSON.stringify(data));
  } catch {
    // ignore
  }
}

interface SearchResult {
  entityType: EntityType;
  data: unknown[];
  count: number;
  executionTimeMs?: number;
  debugInfo?: QueryPlanDebugInfo;
  raw?: Record<string, RawRecord[]>;
  rawResponse?: unknown;
}

function App() {
  const [databases, setDatabases] = useState<Awaited<ReturnType<typeof fetchDatabases>>['databases']>([]);
  const [selectedDb, setSelectedDb] = useState('');
  const [entityType, setEntityType] = useState<EntityType>('Games');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<SearchResult | null>(null);

  const [showQueryPlan, setShowQueryPlan] = useLocalStorage(QUERY_PLAN_PANEL_KEY, false);
  const [showRequestResponse, setShowRequestResponse] = useLocalStorage(
    REQUEST_RESPONSE_PANEL_KEY,
    false
  );

  const [hiddenColumns, setHiddenColumns] = useState<Record<string, string[]>>(() =>
    loadHiddenColumns()
  );

  const [savedSearches, setSavedSearches] = useState<SavedSearch[]>(loadSavedSearches);

  // Session cache for filter options, per database and entity type
  const [filterOptionsCache, setFilterOptionsCache] = useState<
    Record<string, FilterOptionsResponse>
  >({});
  const filterOptionsFor = useCallback(
    (db: string, type: EntityType): FilterOptionsResponse | null =>
      filterOptionsCache[`${db}/${type}`] ?? null,
    [filterOptionsCache]
  );
  useEffect(() => {
    if (!selectedDb || filterOptionsFor(selectedDb, entityType)) return;
    const cacheKey = `${selectedDb}/${entityType}`;
    let cancelled = false;
    fetchFilterOptions(selectedDb, ENTITY_CONFIG[entityType].entityKey)
      .then((data) => {
        if (!cancelled) {
          setFilterOptionsCache((prev) => ({ ...prev, [cacheKey]: data }));
        }
      })
      .catch(() => {
        // Leave cache empty so we can retry when switching entity type again
      });
    return () => {
      cancelled = true;
    };
  }, [selectedDb, entityType]); // eslint-disable-line react-hooks/exhaustive-deps -- fetch only when the database or entity type changes

  const toggleColumn = useCallback((entityKey: string, columnKey: string) => {
    setHiddenColumns((prev) => {
      const arr = prev[entityKey] ?? [];
      const set = new Set(arr);
      if (set.has(columnKey)) set.delete(columnKey);
      else set.add(columnKey);
      const next = { ...prev, [entityKey]: [...set] };
      saveHiddenColumns(next);
      return next;
    });
  }, []);

  // Form state
  const [rowsPerPage, setRowsPerPage] = useState(10);
  const [currentPage, setCurrentPage] = useState(0);
  const [sortBy, setSortBy] = useState('id');
  const [order, setOrder] = useState<'asc' | 'desc'>('asc');
  const [includeMoves, setIncludeMoves] = useState(false);
  const [includeRawData, setIncludeRawData] = useState(false);
  const [executeAllPlansDefault, setExecuteAllPlansDefault] = useState(false);
  const [filter, setFilter] = useState('');

  const loadSavedSearch = useCallback(
    (saved: SavedSearch) => {
      setSelectedDb(saved.selectedDb);
      setEntityType(saved.entityType);
      setFilter(saved.filter);
      const defaultForSaved =
        getSortOptions(saved.entityType, filterOptionsFor(saved.selectedDb, saved.entityType))[0];
      setSortBy(saved.sortBy ?? defaultForSaved);
      setOrder(saved.order ?? 'asc');
      setIncludeMoves(saved.includeMoves ?? false);
      setIncludeRawData(saved.includeRawData ?? false);
    },
    [filterOptionsFor]
  );

  const saveCurrentSearch = useCallback(() => {
    const name =
      window.prompt('Name for this search', filter.slice(0, 40) || 'Untitled search')?.trim();
    if (!name) return;
    const saved: SavedSearch = {
      id: crypto.randomUUID(),
      name,
      savedAt: Date.now(),
      entityType,
      selectedDb,
      filter,
      sortBy,
      order,
      includeMoves,
      includeRawData,
    };
    setSavedSearches((prev) => {
      const next = [...prev, saved];
      persistSavedSearches(next);
      return next;
    });
  }, [entityType, selectedDb, filter, sortBy, order, includeMoves, includeRawData]);

  const removeSavedSearch = useCallback((id: string) => {
    setSavedSearches((prev) => {
      const next = prev.filter((s) => s.id !== id);
      persistSavedSearches(next);
      return next;
    });
  }, []);

  useEffect(() => {
    fetchDatabases()
      .then((data) => {
        setDatabases(data.databases);
        if (data.databases.length > 0 && !selectedDb) {
          setSelectedDb(data.databases[0].id);
        }
      })
      .catch((err: Error) => setError(err.message));
  }, []);

  // Reset sortBy when entity type or filter options change so it stays valid
  const sortOptions = getSortOptions(entityType, filterOptionsFor(selectedDb, entityType));
  useEffect(() => {
    const valid = sortOptions.includes(sortBy);
    if (!valid) setSortBy(sortOptions[0]);
  }, [entityType, sortBy, sortOptions]);

  const entityTypeChangedByUser = useRef(false);

  const handleDbChange = useCallback((newId: string) => {
    setFilter('');
    setSelectedDb(newId);
  }, []);

  const handleEntityTypeChange = useCallback(
    (newType: EntityType) => {
      entityTypeChangedByUser.current = true;
      setEntityType(newType);
      setFilter('');
      const defaultSortOptions = getSortOptions(newType, filterOptionsFor(selectedDb, newType));
      setSortBy(defaultSortOptions[0]);
      setOrder('asc');
    },
    [filterOptionsFor, selectedDb]
  );

  const sortByParam =
    entityType !== 'Games' && sortBy === 'default'
      ? 'default'
      : `${order === 'desc' ? '-' : '+'}${sortBy}`;

  const buildRequest = useCallback(
    (sortBy: string): GameSearchRequest | EntitySearchRequest => {
      const req: GameSearchRequest | EntitySearchRequest =
        entityType === 'Games' ? { sortBy, includeMoves } : { sortBy };
      if (filter.trim()) req.filter = filter.trim();
      return req;
    },
    [entityType, includeMoves, filter]
  );

  const handleSearch = useCallback(
    async (
      executeAllPlans?: boolean,
      sortOverrides?: { sortBy: string; order: 'asc' | 'desc' }
    ) => {
      if (!selectedDb) {
        setError('Select a database first');
        return;
      }
      const effective = executeAllPlans ?? executeAllPlansDefault;
      if (executeAllPlans !== undefined) {
        setExecuteAllPlansDefault(executeAllPlans);
      }
      setError(null);
      setLoading(true);
      setResult(null);
      setCurrentPage(0);

      const effectiveSortBy = sortOverrides?.sortBy ?? sortBy;
      const effectiveOrder = sortOverrides?.order ?? order;
      const effectiveSortByParam =
        entityType !== 'Games' && effectiveSortBy === 'default'
          ? 'default'
          : `${effectiveOrder === 'desc' ? '-' : '+'}${effectiveSortBy}`;

      try {
        const res = await runSearch(
          entityType,
          selectedDb,
          buildRequest(effectiveSortByParam),
          effective
        );
        setResult({ entityType, ...res });
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Search failed');
      } finally {
        setLoading(false);
      }
    },
    [selectedDb, entityType, sortBy, order, buildRequest, executeAllPlansDefault]
  );

  // When user changes entity type from dropdown: filter is cleared, trigger search
  useEffect(() => {
    if (entityTypeChangedByUser.current && selectedDb) {
      entityTypeChangedByUser.current = false;
      handleSearch();
    }
  }, [entityType, selectedDb, handleSearch]);

  // When database changes: filter is already cleared by handleDbChange; run search (keeps entity type)
  useEffect(() => {
    if (selectedDb) {
      handleSearch();
    }
  }, [selectedDb]); // eslint-disable-line react-hooks/exhaustive-deps -- only re-run search when DB changes, not when handleSearch identity changes

  const paginatedData = useMemo(() => {
    if (!result || result.data.length === 0) {
      return { data: [], total: 0, from: 0, to: 0, totalPages: 0, page: 0 };
    }
    const total = result.data.length;
    const totalPages = Math.ceil(total / rowsPerPage) || 1;
    const page = Math.min(currentPage, totalPages - 1);
    const from = page * rowsPerPage;
    const to = Math.min(from + rowsPerPage, total);
    const data = result.data.slice(from, to);
    return { data, total, from: from + 1, to, totalPages, page };
  }, [result, rowsPerPage, currentPage]);

  const handleRowsPerPageChange = useCallback((n: number) => {
    setRowsPerPage(n);
    setCurrentPage(0);
  }, []);

  const handleColumnSort = useCallback(
    (columnKey: string) => {
      const map = SORTABLE_COLUMN_MAP[entityType];
      const sortField = map?.[columnKey];
      if (!sortField) return;
      const filterOptions = filterOptionsFor(selectedDb, entityType);
      const defaultDirection = getDefaultSortDirection(filterOptions, sortField);
      const isCurrentSort = sortBy === sortField;
      const newOrder = isCurrentSort
        ? (order === 'asc' ? 'desc' : 'asc')
        : defaultDirection;
      if (!isCurrentSort) setSortBy(sortField);
      setOrder(newOrder);
      handleSearch(undefined, { sortBy: sortField, order: newOrder });
    },
    [entityType, sortBy, order, filterOptionsFor, selectedDb, handleSearch]
  );

  const hasQueryPlan = Boolean(result?.debugInfo?.plans?.length);

  const requestJson = JSON.stringify(
    { ...buildRequest(sortByParam), executeAllPlans: executeAllPlansDefault },
    null,
    2
  );

  return (
    <div className="app">
      <header className="header">
        <div>
          <h1>Search Tester</h1>
          <p className="subtitle">Debug the morphy-service search API</p>
        </div>
      </header>

      <div className="main">
        <SearchPanel
          databases={databases}
          selectedDb={selectedDb}
          onDbChange={handleDbChange}
          entityType={entityType}
          onEntityTypeChange={handleEntityTypeChange}
          filter={filter}
          onFilterChange={setFilter}
          filterOptions={filterOptionsFor(selectedDb, entityType)}
          loading={loading}
          savedSearches={savedSearches}
          onLoadSavedSearch={loadSavedSearch}
          onSaveSearch={saveCurrentSearch}
          onRemoveSavedSearch={removeSavedSearch}
          includeMoves={includeMoves}
          onIncludeMovesChange={setIncludeMoves}
          includeRawData={includeRawData}
          onIncludeRawDataChange={setIncludeRawData}
          executeAllPlansDefault={executeAllPlansDefault}
          onSearch={handleSearch}
        />

        <ResultsSection
          result={
            result
              ? {
                  entityType: result.entityType,
                  data: result.data,
                  count: result.count,
                  executionTimeMs: result.executionTimeMs,
                  raw: includeRawData ? result.raw : undefined,
                }
              : null
          }
          paginatedData={paginatedData}
          rowsPerPage={rowsPerPage}
          onRowsPerPageChange={handleRowsPerPageChange}
          hiddenColumns={hiddenColumns}
          onToggleColumn={toggleColumn}
          currentPage={currentPage}
          onPageChange={setCurrentPage}
          error={error}
          loading={loading}
          sortBy={sortBy}
          order={order}
          onColumnSort={handleColumnSort}
          entityType={entityType}
        />

        {showQueryPlan && hasQueryPlan && (
          <CollapsiblePanel
            title="Query Plan"
            onToggle={setShowQueryPlan}
            hideLabel="Hide Query Plan"
            panelClass="query-plan-panel"
          >
            <QueryPlanVisualiser debugInfo={result!.debugInfo!} />
          </CollapsiblePanel>
        )}
        {!showQueryPlan && hasQueryPlan && (
          <ShowPanelButton label="Show Query Plan" onClick={() => setShowQueryPlan(true)} />
        )}

        {showRequestResponse && (
          <CollapsiblePanel
            title="Request – Response"
            onToggle={setShowRequestResponse}
            hideLabel="Hide Request – Response"
            panelClass="request-response-panel"
          >
            <div className="request-response-content">
              <div>
                <strong>Request (GET params):</strong>
                <pre>{requestJson}</pre>
              </div>
              {result?.rawResponse != null && (
                <div>
                  <strong>Response:</strong>
                  <pre>{JSON.stringify(result.rawResponse, null, 2)}</pre>
                </div>
              )}
            </div>
          </CollapsiblePanel>
        )}
        {!showRequestResponse && (
          <ShowPanelButton
            label="Show Request – Response"
            onClick={() => setShowRequestResponse(true)}
          />
        )}
      </div>
    </div>
  );
}

export default App;

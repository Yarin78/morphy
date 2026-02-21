import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { CollapsiblePanel, ShowPanelButton } from './CollapsiblePanel';
import { QueryPlanVisualiser } from './QueryPlanVisualiser';
import { ResultsSection } from './ResultsSection';
import { SearchPanel } from './SearchPanel';
import { fetchDatabases, fetchFilterOptions } from './api/client';
import type {
  FilterOptionsResponse,
  GameSearchRequest,
  QueryPlanDebugInfo,
} from './api/types';
import type { EntityType } from './entityConfig';
import type { SavedSearch } from './savedSearchTypes';
import { ENTITY_CONFIG, getSortOptions, SORTABLE_COLUMN_MAP } from './entityConfig';
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
  debugInfo?: unknown;
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

  // Session cache for filter options (static per entity type)
  const [filterOptionsCache, setFilterOptionsCache] = useState<
    Partial<Record<EntityType, FilterOptionsResponse>>
  >({});
  useEffect(() => {
    if (filterOptionsCache[entityType]) return;
    let cancelled = false;
    fetchFilterOptions(entityType)
      .then((data) => {
        if (!cancelled) {
          setFilterOptionsCache((prev) => ({ ...prev, [entityType]: data }));
        }
      })
      .catch(() => {
        // Leave cache empty so we can retry when switching entity type again
      });
    return () => {
      cancelled = true;
    };
  }, [entityType]);

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
  const [executeAllPlansDefault, setExecuteAllPlansDefault] = useState(false);
  const [filter, setFilter] = useState('');

  const loadSavedSearch = useCallback((saved: SavedSearch) => {
    setSelectedDb(saved.selectedDb);
    setEntityType(saved.entityType);
    setFilter(saved.filter);
    setSortBy(saved.sortBy ?? 'id');
    setOrder(saved.order ?? 'asc');
    setIncludeMoves(saved.includeMoves ?? false);
  }, []);

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
    };
    setSavedSearches((prev) => {
      const next = [...prev, saved];
      persistSavedSearches(next);
      return next;
    });
  }, [entityType, selectedDb, filter, sortBy, order, includeMoves]);

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
  const sortOptions = getSortOptions(entityType, filterOptionsCache[entityType] ?? null);
  useEffect(() => {
    const valid = sortOptions.includes(sortBy);
    if (!valid) setSortBy(sortOptions[0]);
  }, [entityType, sortBy, sortOptions]);

  const entityTypeChangedByUser = useRef(false);

  const handleEntityTypeChange = useCallback(
    (newType: EntityType) => {
      entityTypeChangedByUser.current = true;
      setEntityType(newType);
      setFilter('');
      const defaultSortOptions = getSortOptions(
        newType,
        filterOptionsCache[newType] ?? null
      );
      setSortBy(defaultSortOptions[0]);
      setOrder('asc');
    },
    [filterOptionsCache]
  );

  const sortByParam = `${order === 'desc' ? '-' : '+'}${sortBy}`;

  const buildRequest = useCallback(
    (executeAllPlans: boolean): GameSearchRequest => {
      const req: GameSearchRequest = {
        sortBy: sortByParam,
        includeMoves,
        debugQueryPlans: true,
      };
      if (filter.trim()) req.filter = filter.trim();
      if (executeAllPlans) req.debugExecuteAllPlans = true;
      return req;
    },
    [sortByParam, includeMoves, filter]
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
      const effectiveSortByParam = `${effectiveOrder === 'desc' ? '-' : '+'}${effectiveSortBy}`;

      const config = ENTITY_CONFIG[entityType];
      const opts = {
        gameRequest:
          entityType === 'Games'
            ? {
                ...buildRequest(effective),
                sortBy: effectiveSortByParam,
              }
            : undefined,
        entitySearchRequest:
          entityType !== 'Games'
            ? {
                filter: filter.trim() || undefined,
                sortBy: effectiveSortByParam,
                debugQueryPlans: true,
                debugExecuteAllPlans: effective,
              }
            : undefined,
      };

      try {
        const res = await config.fetch(selectedDb, opts);
      setResult({
        entityType,
        data: res.data,
        count: res.count,
        executionTimeMs: res.metadata?.executionTimeMs,
        debugInfo: res.debugInfo,
        rawResponse: res.rawResponse,
      });
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Search failed');
      } finally {
        setLoading(false);
      }
    },
    [selectedDb, entityType, filter, sortByParam, buildRequest, executeAllPlansDefault]
  );

  // When user changes entity type from dropdown: filter is cleared, trigger search
  useEffect(() => {
    if (entityTypeChangedByUser.current && selectedDb) {
      entityTypeChangedByUser.current = false;
      handleSearch();
    }
  }, [entityType, selectedDb, handleSearch]);

  // Trigger empty search on initial page load when database is available
  const hasRunInitialSearch = useRef(false);
  useEffect(() => {
    if (selectedDb && !hasRunInitialSearch.current) {
      hasRunInitialSearch.current = true;
      handleSearch();
    }
  }, [selectedDb, handleSearch]);

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
      const isCurrentSort = sortBy === sortField;
      const newOrder = isCurrentSort
        ? (order === 'asc' ? 'desc' : 'asc')
        : 'asc';
      if (!isCurrentSort) setSortBy(sortField);
      setOrder(newOrder);
      handleSearch(undefined, { sortBy: sortField, order: newOrder });
    },
    [entityType, sortBy, order, handleSearch]
  );

  const hasQueryPlan = Boolean(
    result?.debugInfo &&
      Array.isArray((result.debugInfo as { plans?: unknown[] }).plans) &&
      (result.debugInfo as { plans: unknown[] }).plans.length > 0
  );

  const requestJson =
    entityType === 'Games'
      ? JSON.stringify(buildRequest(executeAllPlansDefault), null, 2)
      : JSON.stringify(
          {
            filter: filter.trim() || undefined,
            sortBy: sortByParam,
            debugQueryPlans: true,
            debugExecuteAllPlans: executeAllPlansDefault,
          },
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
          onDbChange={setSelectedDb}
          entityType={entityType}
          onEntityTypeChange={handleEntityTypeChange}
          filter={filter}
          onFilterChange={setFilter}
          filterOptions={filterOptionsCache[entityType] ?? null}
          loading={loading}
          savedSearches={savedSearches}
          onLoadSavedSearch={loadSavedSearch}
          onSaveSearch={saveCurrentSearch}
          onRemoveSavedSearch={removeSavedSearch}
          includeMoves={includeMoves}
          onIncludeMovesChange={setIncludeMoves}
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
                  debugInfo: result.debugInfo,
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
          sortBy={sortBy}
          order={order}
          onColumnSort={handleColumnSort}
        />

        {showQueryPlan && hasQueryPlan && (
          <CollapsiblePanel
            title="Query Plan"
            onToggle={setShowQueryPlan}
            hideLabel="Hide Query Plan"
            panelClass="query-plan-panel"
          >
            <QueryPlanVisualiser debugInfo={result!.debugInfo as QueryPlanDebugInfo} />
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

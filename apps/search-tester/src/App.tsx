import { useCallback, useEffect, useState } from 'react';
import { ColumnSelector } from './ColumnSelector';
import { RowsPerPageSelector } from './RowsPerPageSelector';
import ResultsTable, {
  ANNOTATOR_COLUMNS,
  GAME_COLUMNS,
  GAMETAG_COLUMNS,
  PLAYER_COLUMNS,
  SOURCE_COLUMNS,
  TEAM_COLUMNS,
  TOURNAMENT_COLUMNS,
} from './ResultsTable';
import {
  fetchAnnotators,
  fetchDatabases,
  fetchGameTags,
  fetchPlayers,
  fetchSources,
  fetchTeams,
  fetchTournaments,
  searchGames,
} from './api/client';
import { QueryPlanVisualiser } from './QueryPlanVisualiser';
import type {
  AnnotatorListResponse,
  DatabaseResponse,
  GameSearchRequest,
  GameSearchResponse,
  GameTagListResponse,
  PlayerListResponse,
  SourceListResponse,
  TeamListResponse,
  TournamentListResponse,
} from './api/types';
import './App.css';

const COLUMN_VISIBILITY_KEY = 'search-tester-hidden-columns';
const QUERY_PLAN_PANEL_KEY = 'search-tester-show-query-plan';
const REQUEST_RESPONSE_PANEL_KEY = 'search-tester-show-request-response';

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

const ENTITY_TYPES = [
  'Games',
  'Players',
  'Tournaments',
  'Annotators',
  'Sources',
  'Teams',
  'GameTags',
] as const;
type EntityType = (typeof ENTITY_TYPES)[number];

const SORT_OPTIONS = ['id', 'date', 'whiteElo', 'blackElo', 'avgElo'] as const;
const ORDER_OPTIONS = ['asc', 'desc'] as const;
const RATING_MODES = ['any', 'both', 'white', 'black', 'average', 'difference'] as const;

type SearchResponse =
  | { type: 'games'; data: GameSearchResponse }
  | { type: 'players'; data: PlayerListResponse }
  | { type: 'tournaments'; data: TournamentListResponse }
  | { type: 'annotators'; data: AnnotatorListResponse }
  | { type: 'sources'; data: SourceListResponse }
  | { type: 'teams'; data: TeamListResponse }
  | { type: 'gametags'; data: GameTagListResponse };

function App() {
  const [databases, setDatabases] = useState<DatabaseResponse[]>([]);
  const [selectedDb, setSelectedDb] = useState('');
  const [entityType, setEntityType] = useState<EntityType>('Games');
  const [showOptions, setShowOptions] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [response, setResponse] = useState<SearchResponse | null>(null);
  const [showQueryPlan, setShowQueryPlan] = useState(() => {
    try {
      return localStorage.getItem(QUERY_PLAN_PANEL_KEY) === 'true';
    } catch {
      return false;
    }
  });

  const [showRequestResponse, setShowRequestResponse] = useState(() => {
    try {
      return localStorage.getItem(REQUEST_RESPONSE_PANEL_KEY) === 'true';
    } catch {
      return false;
    }
  });

  const setShowQueryPlanWithStorage = useCallback((value: boolean) => {
    setShowQueryPlan(value);
    try {
      localStorage.setItem(QUERY_PLAN_PANEL_KEY, String(value));
    } catch {
      // ignore
    }
  }, []);

  const setShowRequestResponseWithStorage = useCallback((value: boolean) => {
    setShowRequestResponse(value);
    try {
      localStorage.setItem(REQUEST_RESPONSE_PANEL_KEY, String(value));
    } catch {
      // ignore
    }
  }, []);
  const [hiddenColumns, setHiddenColumns] = useState<Record<string, string[]>>(
    () => loadHiddenColumns()
  );

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

  const getVisibleColumns = useCallback(
    <T extends { key: string }>(columns: T[], entityKey: string): T[] => {
      const hidden = new Set(hiddenColumns[entityKey] ?? []);
      return columns.filter((c) => !hidden.has(c.key));
    },
    [hiddenColumns]
  );

  const getHiddenSet = useCallback(
    (entityKey: string) => new Set(hiddenColumns[entityKey] ?? []),
    [hiddenColumns]
  );

  // Form state
  const [filter, setFilter] = useState('');
  const [offset, setOffset] = useState(0);
  const [limit, setLimit] = useState(100);
  const [rowsPerPage, setRowsPerPage] = useState(10);
  const [currentPage, setCurrentPage] = useState(0);
  const [sortBy, setSortBy] = useState('id');
  const [order, setOrder] = useState<'asc' | 'desc'>('asc');
  const [includeMoves, setIncludeMoves] = useState(false);
  const [debugExecuteAllPlans, setDebugExecuteAllPlans] = useState(false);
  const [result, setResult] = useState('');
  const [dateFrom, setDateFrom] = useState('');
  const [dateTo, setDateTo] = useState('');
  const [ecoCode, setEcoCode] = useState('');
  const [ratingMin, setRatingMin] = useState('');
  const [ratingMax, setRatingMax] = useState('');
  const [ratingMode, setRatingMode] = useState('any');

  useEffect(() => {
    fetchDatabases()
      .then((data) => {
        setDatabases(data.databases);
        if (data.databases.length > 0 && !selectedDb) {
          setSelectedDb(data.databases[0].id);
        }
      })
      .catch((err) => setError(err.message));
  }, []);

  const buildRequest = (): GameSearchRequest => {
    const req: GameSearchRequest = {
      offset,
      limit,
      sortBy,
      order,
      includeMoves,
      debugQueryPlans: true, // Always request query plans for Games search
    };
    if (filter.trim()) req.filter = filter.trim();
    if (result) req.result = result;
    if (dateFrom) req.dateFrom = dateFrom;
    if (dateTo) req.dateTo = dateTo;
    if (ecoCode) req.ecoCode = ecoCode;
    if (ratingMin) req.ratingMin = parseInt(ratingMin, 10);
    if (ratingMax) req.ratingMax = parseInt(ratingMax, 10);
    if (ratingMode) req.ratingMode = ratingMode;
    if (debugExecuteAllPlans) req.debugExecuteAllPlans = true;
    return req;
  };

  const handleSearch = async () => {
    if (!selectedDb) {
      setError('Select a database first');
      return;
    }
    setError(null);
    setLoading(true);
    setResponse(null);
    setCurrentPage(0);
    try {
      if (entityType === 'Games') {
        const req = buildRequest();
        const res = await searchGames(selectedDb, req);
        setResponse({ type: 'games', data: res });
      } else if (entityType === 'Players') {
        const res = await fetchPlayers(selectedDb, undefined, limit);
        setResponse({ type: 'players', data: res });
      } else if (entityType === 'Tournaments') {
        const res = await fetchTournaments(selectedDb, undefined, limit);
        setResponse({ type: 'tournaments', data: res });
      } else if (entityType === 'Annotators') {
        const res = await fetchAnnotators(selectedDb, undefined, limit);
        setResponse({ type: 'annotators', data: res });
      } else if (entityType === 'Sources') {
        const res = await fetchSources(selectedDb, undefined, limit);
        setResponse({ type: 'sources', data: res });
      } else if (entityType === 'Teams') {
        const res = await fetchTeams(selectedDb, undefined, limit);
        setResponse({ type: 'teams', data: res });
      } else {
        const res = await fetchGameTags(selectedDb, undefined, limit);
        setResponse({ type: 'gametags', data: res });
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Search failed');
    } finally {
      setLoading(false);
    }
  };

  const getResponseData = useCallback((): { data: unknown[]; total: number } | null => {
    if (!response) return null;
    switch (response.type) {
      case 'games':
        return { data: response.data.games, total: response.data.games.length };
      case 'players':
        return { data: response.data.players, total: response.data.players.length };
      case 'tournaments':
        return { data: response.data.tournaments, total: response.data.tournaments.length };
      case 'annotators':
        return { data: response.data.annotators, total: response.data.annotators.length };
      case 'sources':
        return { data: response.data.sources, total: response.data.sources.length };
      case 'teams':
        return { data: response.data.teams, total: response.data.teams.length };
      case 'gametags':
        return { data: response.data.gameTags, total: response.data.gameTags.length };
      default:
        return null;
    }
  }, [response]);

  const paginatedData = (() => {
    const rd = getResponseData();
    if (!rd || rd.total === 0)
      return { data: [], total: 0, from: 0, to: 0, totalPages: 0, page: 0 };
    const totalPages = Math.ceil(rd.total / rowsPerPage) || 1;
    const page = Math.min(currentPage, totalPages - 1);
    const from = page * rowsPerPage;
    const to = Math.min(from + rowsPerPage, rd.total);
    const data = rd.data.slice(from, to);
    return { data, total: rd.total, from: from + 1, to, totalPages, page };
  })();

  const handleRowsPerPageChange = useCallback((n: number) => {
    setRowsPerPage(n);
    setCurrentPage(0);
  }, []);

  const getFilterPlaceholder = () => {
    if (entityType === 'Games') {
      return 'e.g. result:1-0 AND rating:2600.. AND player.name:Carlsen';
    }
    return 'Filter not yet supported for this entity type';
  };

  return (
    <div className="app">
      <header className="header">
        <div>
          <h1>Search Tester</h1>
          <p className="subtitle">Debug the morphy-service search API</p>
        </div>
      </header>

      <div className="main">
        <section className="panel search-panel">
          <div className="field">
            <label>Database</label>
            <select
              value={selectedDb}
              onChange={(e) => setSelectedDb(e.target.value)}
              disabled={!databases.length}
            >
              {!databases.length && <option value="">Loading...</option>}
              {databases.map((db) => (
                <option key={db.id} value={db.id}>
                  {db.displayName} ({db.id})
                </option>
              ))}
            </select>
          </div>

          <div className="search-row">
            <div className="field field-entity-type">
              <label>Type</label>
              <select
                value={entityType}
                onChange={(e) => setEntityType(e.target.value as EntityType)}
              >
                {ENTITY_TYPES.map((t) => (
                  <option key={t} value={t}>
                    {t}
                  </option>
                ))}
              </select>
            </div>
            <div className="field field-filter">
              <label>Filter</label>
              <input
                type="text"
                placeholder={getFilterPlaceholder()}
                value={filter}
                onChange={(e) => setFilter(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
                disabled={entityType !== 'Games'}
              />
            </div>
            <button
              className="search-btn search-btn-inline"
              onClick={handleSearch}
              disabled={loading}
            >
              {loading ? 'Searching...' : 'Search'}
            </button>
          </div>

          <div className="options-toggle">
            <button
              type="button"
              className="options-toggle-btn"
              onClick={() => setShowOptions(!showOptions)}
              aria-expanded={showOptions}
            >
              {showOptions ? '−' : '+'} Optional parameters
            </button>
          </div>

          {showOptions && (
            <div className="options-panel">
              <div className="row">
                <div className="field">
                  <label>Offset</label>
                  <input
                    type="number"
                    min={0}
                    value={offset}
                    onChange={(e) => setOffset(parseInt(e.target.value, 10) || 0)}
                  />
                </div>
                <div className="field">
                  <label>Limit</label>
                  <input
                    type="number"
                    min={1}
                    max={1000}
                    value={limit}
                    onChange={(e) => setLimit(parseInt(e.target.value, 10) || 100)}
                  />
                </div>
                {entityType === 'Games' && (
                  <>
                    <div className="field">
                      <label>Sort by</label>
                      <select value={sortBy} onChange={(e) => setSortBy(e.target.value)}>
                        {SORT_OPTIONS.map((s) => (
                          <option key={s} value={s}>
                            {s}
                          </option>
                        ))}
                      </select>
                    </div>
                    <div className="field">
                      <label>Order</label>
                      <select value={order} onChange={(e) => setOrder(e.target.value as 'asc' | 'desc')}>
                        {ORDER_OPTIONS.map((o) => (
                          <option key={o} value={o}>
                            {o}
                          </option>
                        ))}
                      </select>
                    </div>
                  </>
                )}
              </div>

              {entityType === 'Games' && (
                <>
                  <div className="row">
                    <div className="field">
                      <label>Result</label>
                      <input
                        placeholder="1-0, 0-1, 1/2-1/2"
                        value={result}
                        onChange={(e) => setResult(e.target.value)}
                      />
                    </div>
                    <div className="field">
                      <label>Date from</label>
                      <input
                        type="date"
                        value={dateFrom}
                        onChange={(e) => setDateFrom(e.target.value)}
                      />
                    </div>
                    <div className="field">
                      <label>Date to</label>
                      <input type="date" value={dateTo} onChange={(e) => setDateTo(e.target.value)} />
                    </div>
                    <div className="field">
                      <label>ECO</label>
                      <input
                        placeholder="B9*"
                        value={ecoCode}
                        onChange={(e) => setEcoCode(e.target.value)}
                      />
                    </div>
                  </div>

                  <div className="row">
                    <div className="field">
                      <label>Rating min</label>
                      <input
                        type="number"
                        placeholder="2600"
                        value={ratingMin}
                        onChange={(e) => setRatingMin(e.target.value)}
                      />
                    </div>
                    <div className="field">
                      <label>Rating max</label>
                      <input
                        type="number"
                        placeholder="2800"
                        value={ratingMax}
                        onChange={(e) => setRatingMax(e.target.value)}
                      />
                    </div>
                    <div className="field">
                      <label>Rating mode</label>
                      <select value={ratingMode} onChange={(e) => setRatingMode(e.target.value)}>
                        {RATING_MODES.map((m) => (
                          <option key={m} value={m}>
                            {m}
                          </option>
                        ))}
                      </select>
                    </div>
                    <div className="field checkbox-field">
                      <label>
                        <input
                          type="checkbox"
                          checked={includeMoves}
                          onChange={(e) => setIncludeMoves(e.target.checked)}
                        />
                        Include moves
                      </label>
                    </div>
                    <div className="field checkbox-field">
                      <label>
                        <input
                          type="checkbox"
                          checked={debugExecuteAllPlans}
                          onChange={(e) =>
                            setDebugExecuteAllPlans(e.target.checked)
                          }
                        />
                        Execute all plans
                      </label>
                    </div>
                  </div>
                </>
              )}
            </div>
          )}
        </section>

        <section className="panel results-panel">
          <h2>
            Results
            {response && (
              <span className="results-header-actions">
                <span className="meta">
                  {paginatedData.total > 0 ? (
                    <>
                      Showing {paginatedData.from}-{paginatedData.to} of{' '}
                      {paginatedData.total}
                      {response.type === 'games' &&
                        response.data.metadata.executionTimeMs !== undefined &&
                        ` · ${response.data.metadata.executionTimeMs}ms`}
                    </>
                  ) : (
                    <>
                      {response.type === 'games' && `${response.data.count} games`}
                      {response.type === 'players' && `${response.data.count} players`}
                      {response.type === 'tournaments' && `${response.data.count} tournaments`}
                      {response.type === 'annotators' && `${response.data.count} annotators`}
                      {response.type === 'sources' && `${response.data.count} sources`}
                      {response.type === 'teams' && `${response.data.count} teams`}
                      {response.type === 'gametags' && `${response.data.count} game tags`}
                    </>
                  )}
                </span>
                <RowsPerPageSelector
                  value={rowsPerPage}
                  onChange={handleRowsPerPageChange}
                />
                {response.type === 'games' && (
                  <ColumnSelector
                    columns={GAME_COLUMNS}
                    hiddenKeys={getHiddenSet('games')}
                    onToggle={(k) => toggleColumn('games', k)}
                  />
                )}
                {response.type === 'players' && (
                  <ColumnSelector
                    columns={PLAYER_COLUMNS}
                    hiddenKeys={getHiddenSet('players')}
                    onToggle={(k) => toggleColumn('players', k)}
                  />
                )}
                {response.type === 'tournaments' && (
                  <ColumnSelector
                    columns={TOURNAMENT_COLUMNS}
                    hiddenKeys={getHiddenSet('tournaments')}
                    onToggle={(k) => toggleColumn('tournaments', k)}
                  />
                )}
                {response.type === 'annotators' && (
                  <ColumnSelector
                    columns={ANNOTATOR_COLUMNS}
                    hiddenKeys={getHiddenSet('annotators')}
                    onToggle={(k) => toggleColumn('annotators', k)}
                  />
                )}
                {response.type === 'sources' && (
                  <ColumnSelector
                    columns={SOURCE_COLUMNS}
                    hiddenKeys={getHiddenSet('sources')}
                    onToggle={(k) => toggleColumn('sources', k)}
                  />
                )}
                {response.type === 'teams' && (
                  <ColumnSelector
                    columns={TEAM_COLUMNS}
                    hiddenKeys={getHiddenSet('teams')}
                    onToggle={(k) => toggleColumn('teams', k)}
                  />
                )}
                {response.type === 'gametags' && (
                  <ColumnSelector
                    columns={GAMETAG_COLUMNS}
                    hiddenKeys={getHiddenSet('gametags')}
                    onToggle={(k) => toggleColumn('gametags', k)}
                  />
                )}
              </span>
            )}
          </h2>

          {error && <div className="error">{error}</div>}

          {response && (
            <>
              {response.type === 'games' && (
                <ResultsTable
                  columns={getVisibleColumns(GAME_COLUMNS, 'games')}
                  data={paginatedData.data as GameSearchResponse['games']}
                  keyExtractor={(g: { id: number }) => g.id}
                  emptyMessage="No games match the search criteria."
                />
              )}
              {response.type === 'players' && (
                <ResultsTable
                  columns={getVisibleColumns(PLAYER_COLUMNS, 'players')}
                  data={paginatedData.data as PlayerListResponse['players']}
                  keyExtractor={(p: { id: number }) => p.id}
                  emptyMessage="No players found."
                />
              )}
              {response.type === 'tournaments' && (
                <ResultsTable
                  columns={getVisibleColumns(TOURNAMENT_COLUMNS, 'tournaments')}
                  data={paginatedData.data as TournamentListResponse['tournaments']}
                  keyExtractor={(t: { id: number }) => t.id}
                  emptyMessage="No tournaments found."
                />
              )}
              {response.type === 'annotators' && (
                <ResultsTable
                  columns={getVisibleColumns(ANNOTATOR_COLUMNS, 'annotators')}
                  data={paginatedData.data as AnnotatorListResponse['annotators']}
                  keyExtractor={(a: { id: number }) => a.id}
                  emptyMessage="No annotators found."
                />
              )}
              {response.type === 'sources' && (
                <ResultsTable
                  columns={getVisibleColumns(SOURCE_COLUMNS, 'sources')}
                  data={paginatedData.data as SourceListResponse['sources']}
                  keyExtractor={(s: { id: number }) => s.id}
                  emptyMessage="No sources found."
                />
              )}
              {response.type === 'teams' && (
                <ResultsTable
                  columns={getVisibleColumns(TEAM_COLUMNS, 'teams')}
                  data={paginatedData.data as TeamListResponse['teams']}
                  keyExtractor={(t: { id: number }) => t.id}
                  emptyMessage="No teams found."
                />
              )}
              {response.type === 'gametags' && (
                <ResultsTable
                  columns={getVisibleColumns(GAMETAG_COLUMNS, 'gametags')}
                  data={paginatedData.data as GameTagListResponse['gameTags']}
                  keyExtractor={(g: { id: number }) => g.id}
                  emptyMessage="No game tags found."
                />
              )}
              {paginatedData.totalPages > 1 && (
                <div className="pagination">
                  <button
                    type="button"
                    className="pagination-btn"
                    disabled={paginatedData.page === 0}
                    onClick={() => setCurrentPage((p) => Math.max(0, p - 1))}
                  >
                    Previous
                  </button>
                  <span className="pagination-info">
                    Page {paginatedData.page + 1} of {paginatedData.totalPages}
                  </span>
                  <button
                    type="button"
                    className="pagination-btn"
                    disabled={paginatedData.page >= paginatedData.totalPages - 1}
                    onClick={() =>
                      setCurrentPage((p) =>
                        Math.min(paginatedData.totalPages - 1, p + 1)
                      )
                    }
                  >
                    Next
                  </button>
                </div>
              )}
            </>
          )}
        </section>

        {showQueryPlan &&
          response?.type === 'games' &&
          response.data.debugInfo &&
          response.data.debugInfo.plans.length > 0 && (
            <section className="panel query-plan-panel">
              <h2>
                Query Plan
                <button
                  className="panel-toggle"
                  onClick={() => setShowQueryPlanWithStorage(false)}
                  aria-label="Hide Query Plan pane"
                >
                  −
                </button>
              </h2>
              <QueryPlanVisualiser debugInfo={response.data.debugInfo} />
            </section>
          )}
        {!showQueryPlan &&
          response?.type === 'games' &&
          response.data.debugInfo &&
          response.data.debugInfo.plans.length > 0 && (
            <button
              className="show-panel-btn"
              onClick={() => setShowQueryPlanWithStorage(true)}
            >
              Show Query Plan
            </button>
          )}
        {showRequestResponse && (
          <section className="panel request-response-panel">
            <h2>
              Request – Response
              <button
                className="panel-toggle"
                onClick={() => setShowRequestResponseWithStorage(false)}
                aria-label="Hide Request – Response pane"
              >
                −
              </button>
            </h2>
            <div className="request-response-content">
              <div>
                <strong>Request (GET params):</strong>
                <pre>
                  {entityType === 'Games'
                    ? JSON.stringify(buildRequest(), null, 2)
                    : JSON.stringify({ limit }, null, 2)}
                </pre>
              </div>
              {response && (
                <div>
                  <strong>Response:</strong>
                  <pre>
                    {JSON.stringify(
                      response.type === 'games'
                        ? response.data
                        : response.type === 'players'
                          ? response.data
                          : response.data,
                      null,
                      2
                    )}
                  </pre>
                </div>
              )}
            </div>
          </section>
        )}
        {!showRequestResponse && (
          <button
            className="show-panel-btn"
            onClick={() => setShowRequestResponseWithStorage(true)}
          >
            Show Request – Response
          </button>
        )}
      </div>
    </div>
  );
}

export default App;

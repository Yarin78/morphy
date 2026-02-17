import type { DatabaseResponse } from './api/types';
import type { EntityType } from './entityConfig';
import {
  ENTITY_TYPES,
  ORDER_OPTIONS,
  RATING_MODES,
  SORT_OPTIONS,
} from './entityConfig';

interface SearchPanelProps {
  databases: DatabaseResponse[];
  selectedDb: string;
  onDbChange: (id: string) => void;
  entityType: EntityType;
  onEntityTypeChange: (t: EntityType) => void;
  filter: string;
  onFilterChange: (v: string) => void;
  onSearch: () => void;
  loading: boolean;
  showOptions: boolean;
  onShowOptionsChange: (v: boolean) => void;
  // Options panel state
  offset: number;
  onOffsetChange: (v: number) => void;
  limit: number;
  onLimitChange: (v: number) => void;
  sortBy: string;
  onSortByChange: (v: string) => void;
  order: 'asc' | 'desc';
  onOrderChange: (v: 'asc' | 'desc') => void;
  result: string;
  onResultChange: (v: string) => void;
  dateFrom: string;
  onDateFromChange: (v: string) => void;
  dateTo: string;
  onDateToChange: (v: string) => void;
  ecoCode: string;
  onEcoCodeChange: (v: string) => void;
  ratingMin: string;
  onRatingMinChange: (v: string) => void;
  ratingMax: string;
  onRatingMaxChange: (v: string) => void;
  ratingMode: string;
  onRatingModeChange: (v: string) => void;
  includeMoves: boolean;
  onIncludeMovesChange: (v: boolean) => void;
  debugExecuteAllPlans: boolean;
  onDebugExecuteAllPlansChange: (v: boolean) => void;
}

function getFilterPlaceholder(entityType: EntityType): string {
  if (entityType === 'Games') {
    return 'e.g. result:1-0 AND rating:2600.. AND player.name:Carlsen';
  }
  return 'Filter not yet supported for this entity type';
}

export function SearchPanel({
  databases,
  selectedDb,
  onDbChange,
  entityType,
  onEntityTypeChange,
  filter,
  onFilterChange,
  onSearch,
  loading,
  showOptions,
  onShowOptionsChange,
  offset,
  onOffsetChange,
  limit,
  onLimitChange,
  sortBy,
  onSortByChange,
  order,
  onOrderChange,
  result,
  onResultChange,
  dateFrom,
  onDateFromChange,
  dateTo,
  onDateToChange,
  ecoCode,
  onEcoCodeChange,
  ratingMin,
  onRatingMinChange,
  ratingMax,
  onRatingMaxChange,
  ratingMode,
  onRatingModeChange,
  includeMoves,
  onIncludeMovesChange,
  debugExecuteAllPlans,
  onDebugExecuteAllPlansChange,
}: SearchPanelProps) {
  return (
    <section className="panel search-panel">
      <div className="field">
        <label>Database</label>
        <select
          value={selectedDb}
          onChange={(e) => onDbChange(e.target.value)}
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
            onChange={(e) => onEntityTypeChange(e.target.value as EntityType)}
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
            placeholder={getFilterPlaceholder(entityType)}
            value={filter}
            onChange={(e) => onFilterChange(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && onSearch()}
            disabled={entityType !== 'Games'}
          />
        </div>
        <button
          className="search-btn search-btn-inline"
          onClick={onSearch}
          disabled={loading}
        >
          {loading ? 'Searching...' : 'Search'}
        </button>
      </div>

      <div className="options-toggle">
        <button
          type="button"
          className="options-toggle-btn"
          onClick={() => onShowOptionsChange(!showOptions)}
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
                onChange={(e) => onOffsetChange(parseInt(e.target.value, 10) || 0)}
              />
            </div>
            <div className="field">
              <label>Limit</label>
              <input
                type="number"
                min={1}
                max={1000}
                value={limit}
                onChange={(e) => onLimitChange(parseInt(e.target.value, 10) || 100)}
              />
            </div>
            {entityType === 'Games' && (
              <>
                <div className="field">
                  <label>Sort by</label>
                  <select value={sortBy} onChange={(e) => onSortByChange(e.target.value)}>
                    {SORT_OPTIONS.map((s) => (
                      <option key={s} value={s}>
                        {s}
                      </option>
                    ))}
                  </select>
                </div>
                <div className="field">
                  <label>Order</label>
                  <select value={order} onChange={(e) => onOrderChange(e.target.value as 'asc' | 'desc')}>
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
                    onChange={(e) => onResultChange(e.target.value)}
                  />
                </div>
                <div className="field">
                  <label>Date from</label>
                  <input
                    type="date"
                    value={dateFrom}
                    onChange={(e) => onDateFromChange(e.target.value)}
                  />
                </div>
                <div className="field">
                  <label>Date to</label>
                  <input type="date" value={dateTo} onChange={(e) => onDateToChange(e.target.value)} />
                </div>
                <div className="field">
                  <label>ECO</label>
                  <input
                    placeholder="B9*"
                    value={ecoCode}
                    onChange={(e) => onEcoCodeChange(e.target.value)}
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
                    onChange={(e) => onRatingMinChange(e.target.value)}
                  />
                </div>
                <div className="field">
                  <label>Rating max</label>
                  <input
                    type="number"
                    placeholder="2800"
                    value={ratingMax}
                    onChange={(e) => onRatingMaxChange(e.target.value)}
                  />
                </div>
                <div className="field">
                  <label>Rating mode</label>
                  <select value={ratingMode} onChange={(e) => onRatingModeChange(e.target.value)}>
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
                      onChange={(e) => onIncludeMovesChange(e.target.checked)}
                    />
                    Include moves
                  </label>
                </div>
                <div className="field checkbox-field">
                  <label>
                    <input
                      type="checkbox"
                      checked={debugExecuteAllPlans}
                      onChange={(e) => onDebugExecuteAllPlansChange(e.target.checked)}
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
  );
}

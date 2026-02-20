import { useEffect, useRef, useState } from 'react';
import type { DatabaseResponse } from './api/types';
import type { EntityType } from './entityConfig';
import type { SavedSearch } from './savedSearchTypes';
import {
  ENTITY_SORT_OPTIONS,
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
  loading: boolean;
  showOptions: boolean;
  onShowOptionsChange: (v: boolean) => void;
  savedSearches: SavedSearch[];
  onLoadSavedSearch: (saved: SavedSearch) => void;
  onSaveSearch: () => void;
  onRemoveSavedSearch: (id: string) => void;
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
  executeAllPlansDefault: boolean;
  onSearch: (executeAllPlans?: boolean) => void;
}

function getFilterPlaceholder(entityType: EntityType): string {
  switch (entityType) {
    case 'Games':
      return 'e.g. result:1-0 AND rating:2600.. AND player.name:Carlsen';
    case 'Players':
      return 'e.g. Carlsen or Carl';
    case 'Tournaments':
      return 'e.g. Candidates or World';
    case 'Annotators':
    case 'Sources':
    case 'Teams':
    case 'GameTags':
      return 'e.g. partial name to search';
    default:
      return 'Search filter';
  }
}

function getSortOptions(entityType: EntityType): readonly string[] {
  if (entityType === 'Games') return SORT_OPTIONS;
  return ENTITY_SORT_OPTIONS[entityType] ?? ['id'];
}

export function SearchPanel({
  databases,
  selectedDb,
  onDbChange,
  entityType,
  onEntityTypeChange,
  filter,
  onFilterChange,
  loading,
  showOptions,
  onShowOptionsChange,
  savedSearches,
  onLoadSavedSearch,
  onSaveSearch,
  onRemoveSavedSearch,
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
  executeAllPlansDefault,
  onSearch,
}: SearchPanelProps) {
  const [savedSearchOpen, setSavedSearchOpen] = useState(false);
  const [searchDropdownOpen, setSearchDropdownOpen] = useState(false);
  const savedSearchRef = useRef<HTMLDivElement>(null);
  const searchDropdownRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!savedSearchOpen) return;
    const handleClick = (e: MouseEvent) => {
      if (savedSearchRef.current && !savedSearchRef.current.contains(e.target as Node)) {
        setSavedSearchOpen(false);
      }
    };
    document.addEventListener('click', handleClick);
    return () => document.removeEventListener('click', handleClick);
  }, [savedSearchOpen]);

  useEffect(() => {
    if (!searchDropdownOpen) return;
    const handleClick = (e: MouseEvent) => {
      if (searchDropdownRef.current && !searchDropdownRef.current.contains(e.target as Node)) {
        setSearchDropdownOpen(false);
      }
    };
    document.addEventListener('click', handleClick);
    return () => document.removeEventListener('click', handleClick);
  }, [searchDropdownOpen]);

  return (
    <section className="panel search-panel">
      <div className="database-row">
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
        {savedSearches.length > 0 && (
          <div className="field saved-searches-field">
            <label>Saved searches</label>
            <div className="saved-searches-dropdown" ref={savedSearchRef}>
              <button
                type="button"
                className="saved-searches-btn"
                onClick={() => setSavedSearchOpen((o) => !o)}
                aria-expanded={savedSearchOpen}
              >
                Load saved search...
              </button>
              {savedSearchOpen && (
                <div className="saved-searches-popover">
                  {savedSearches.map((s) => (
                    <div
                      key={s.id}
                      className="saved-search-item"
                      role="button"
                      tabIndex={0}
                      onClick={() => {
                        onLoadSavedSearch(s);
                        setSavedSearchOpen(false);
                      }}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter' || e.key === ' ') {
                          e.preventDefault();
                          onLoadSavedSearch(s);
                          setSavedSearchOpen(false);
                        }
                      }}
                    >
                      <span className="saved-search-name">{s.name}</span>
                      <button
                        type="button"
                        className="saved-search-delete"
                        aria-label={`Remove ${s.name}`}
                        onClick={(e) => {
                          e.stopPropagation();
                          onRemoveSavedSearch(s.id);
                        }}
                      >
                        ×
                      </button>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        )}
      </div>

      <div className="search-row">
        <div className="field field-entity-type">
          <label>Type</label>
          <select
            className="entity-type-select"
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
          />
        </div>
        <div className="search-btn-split" ref={searchDropdownRef}>
          <button
            className="search-btn search-btn-inline search-btn-main"
            onClick={() => onSearch()}
            disabled={loading}
          >
            {loading ? 'Searching...' : executeAllPlansDefault ? 'Search (all plans)' : 'Search'}
          </button>
          <button
            type="button"
            className="search-btn search-btn-inline search-btn-dropdown"
            onClick={() => setSearchDropdownOpen((o) => !o)}
            disabled={loading}
            aria-expanded={searchDropdownOpen}
            aria-haspopup="true"
          >
            ▾
          </button>
          {searchDropdownOpen && (
            <div className="search-dropdown-popover">
              <button
                type="button"
                className="search-dropdown-item"
                onClick={() => {
                  onSearch(false);
                  setSearchDropdownOpen(false);
                }}
              >
                Search {!executeAllPlansDefault && '✓'}
              </button>
              <button
                type="button"
                className="search-dropdown-item"
                onClick={() => {
                  onSearch(true);
                  setSearchDropdownOpen(false);
                }}
              >
                Search (all plans) {executeAllPlansDefault && '✓'}
              </button>
            </div>
          )}
        </div>
        <button
          type="button"
          className="search-btn search-btn-inline search-btn-save"
          onClick={onSaveSearch}
          title="Save current search"
        >
          Save
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
            <div className="field">
              <label>Sort by</label>
              <select value={sortBy} onChange={(e) => onSortByChange(e.target.value)}>
                {getSortOptions(entityType).map((s) => (
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
              </div>
            </>
          )}
        </div>
      )}
    </section>
  );
}

import { useEffect, useRef, useState } from 'react';
import type { DatabaseResponse, FilterOptionsResponse } from './api/types';
import type { EntityType } from './entityConfig';
import type { SavedSearch } from './savedSearchTypes';
import { ENTITY_TYPES } from './entityConfig';

interface SearchPanelProps {
  databases: DatabaseResponse[];
  selectedDb: string;
  onDbChange: (id: string) => void;
  entityType: EntityType;
  onEntityTypeChange: (t: EntityType) => void;
  filter: string;
  onFilterChange: (v: string) => void;
  filterOptions: FilterOptionsResponse | null;
  loading: boolean;
  savedSearches: SavedSearch[];
  onLoadSavedSearch: (saved: SavedSearch) => void;
  onSaveSearch: () => void;
  onRemoveSavedSearch: (id: string) => void;
  includeMoves: boolean;
  onIncludeMovesChange: (v: boolean) => void;
  includeRawData: boolean;
  onIncludeRawDataChange: (v: boolean) => void;
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

export function SearchPanel({
  databases,
  selectedDb,
  onDbChange,
  entityType,
  onEntityTypeChange,
  filter,
  onFilterChange,
  filterOptions,
  loading,
  savedSearches,
  onLoadSavedSearch,
  onSaveSearch,
  onRemoveSavedSearch,
  includeMoves,
  onIncludeMovesChange,
  includeRawData,
  onIncludeRawDataChange,
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
          {filterOptions && (
            <p className="filter-help">
              Fields:{' '}
              {(() => {
                const { defaultField, fields } = filterOptions;
                const ordered = [
                  defaultField,
                  ...fields.filter((f) => f !== defaultField),
                ];
                return ordered.map((field, i) => (
                  <span key={field}>
                    {i > 0 && ', '}
                    {field === defaultField ? (
                      <>
                        <code>{field}</code>
                      </>
                    ) : (
                      field
                    )}
                  </span>
                ));
              })()}
            </p>
          )}
          <div className="field checkbox-field checkbox-inline checkbox-row">
            {entityType === 'Games' && (
              <label>
                <input
                  type="checkbox"
                  checked={includeMoves}
                  onChange={(e) => onIncludeMovesChange(e.target.checked)}
                />
                Include moves
              </label>
            )}
            <label>
              <input
                type="checkbox"
                checked={includeRawData}
                onChange={(e) => onIncludeRawDataChange(e.target.checked)}
              />
              Include raw data
            </label>
          </div>
        </div>
        <div className="search-btn-split" ref={searchDropdownRef}>
          <button
            className="search-btn search-btn-inline search-btn-main"
            onClick={() => onSearch()}
            disabled={loading}
          >
            {executeAllPlansDefault ? 'Search (all plans)' : 'Search'}
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
    </section>
  );
}

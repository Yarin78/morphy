import { useCallback, useEffect, useMemo, useState } from 'react';
import { ColumnSelector } from './ColumnSelector';
import { RowsPerPageSelector } from './RowsPerPageSelector';
import ResultsTable from './ResultsTable';
import type { EntityType } from './entityConfig';
import { ENTITY_CONFIG, SORTABLE_COLUMN_MAP } from './entityConfig';

const COL_WIDTHS_STORAGE_PREFIX = 'search-tester:colWidths:';

function loadColumnWidths(entityKey: string): Record<string, number> {
  try {
    const raw = localStorage.getItem(COL_WIDTHS_STORAGE_PREFIX + entityKey);
    return raw ? JSON.parse(raw) : {};
  } catch {
    return {};
  }
}

function saveColumnWidths(entityKey: string, widths: Record<string, number>) {
  try {
    if (Object.keys(widths).length === 0) {
      localStorage.removeItem(COL_WIDTHS_STORAGE_PREFIX + entityKey);
    } else {
      localStorage.setItem(COL_WIDTHS_STORAGE_PREFIX + entityKey, JSON.stringify(widths));
    }
  } catch { /* ignore quota errors */ }
}

interface PaginatedData {
  data: unknown[];
  total: number;
  from: number;
  to: number;
  totalPages: number;
  page: number;
}

interface SearchResult {
  entityType: EntityType;
  data: unknown[];
  count: number;
  executionTimeMs?: number;
  debugInfo?: unknown;
}

interface ResultsSectionProps {
  result: SearchResult | null;
  paginatedData: PaginatedData;
  rowsPerPage: number;
  onRowsPerPageChange: (n: number) => void;
  hiddenColumns: Record<string, string[]>;
  onToggleColumn: (entityKey: string, columnKey: string) => void;
  currentPage: number;
  onPageChange: (page: number) => void;
  error: string | null;
  /** True while a search request is in progress. */
  loading?: boolean;
  /** Current sort field (API name). Used to show sort indicator on the matching column. */
  sortBy?: string;
  /** Current sort direction. */
  order?: 'asc' | 'desc';
  /** Called when user clicks a sortable column header. */
  onColumnSort?: (columnKey: string) => void;
}

export function ResultsSection({
  result,
  paginatedData,
  rowsPerPage,
  onRowsPerPageChange,
  hiddenColumns,
  onToggleColumn,
  currentPage,
  onPageChange,
  error,
  loading,
  sortBy,
  order,
  onColumnSort,
}: ResultsSectionProps) {
  const entityKey = result ? ENTITY_CONFIG[result.entityType].entityKey : '';
  const [columnWidths, setColumnWidths] = useState<Record<string, number>>({});

  useEffect(() => {
    setColumnWidths(entityKey ? loadColumnWidths(entityKey) : {});
  }, [entityKey]);

  const handleColumnResize = useCallback(
    (key: string, width: number) => {
      setColumnWidths((prev) => {
        const next = { ...prev, [key]: width };
        if (entityKey) saveColumnWidths(entityKey, next);
        return next;
      });
    },
    [entityKey]
  );

  const handleResetColumnWidths = useCallback(() => {
    setColumnWidths({});
    if (entityKey) saveColumnWidths(entityKey, {});
  }, [entityKey]);

  const hasCustomWidths = Object.keys(columnWidths).length > 0;

  const config = result ? ENTITY_CONFIG[result.entityType] : null;
  const hiddenSet = new Set((config && hiddenColumns[config.entityKey]) ?? []);
  const visibleColumns = config?.columns.filter((c) => !hiddenSet.has(c.key)) ?? [];

  const columnSortConfig = useMemo(() => {
    if (!result) return { sortableColumnKeys: new Set<string>(), sortColumnKey: null };
    const map = SORTABLE_COLUMN_MAP[result.entityType] ?? {};
    const sortableColumnKeys = new Set(Object.keys(map));
    const sortColumnKey =
      visibleColumns.find((col) => map[col.key] === sortBy)?.key ?? null;
    return { sortableColumnKeys, sortColumnKey };
  }, [result, sortBy, visibleColumns]);

  if (!result || !config) {
    return (
      <section className="panel results-panel">
        <h2>
          Results
          <span className="results-header-actions" aria-hidden="true" />
        </h2>
        {loading && <p className="results-loading">Searching…</p>}
        {error && <div className="error">{error}</div>}
      </section>
    );
  }

  const metaText =
    paginatedData.total > 0 ? (
      <>
        Showing {paginatedData.from}-{paginatedData.to} of {paginatedData.total}
        {result.executionTimeMs !== undefined && ` · ${result.executionTimeMs}ms`}
      </>
    ) : (
      `${result.count} ${config.countLabel}`
    );

  return (
    <section className="panel results-panel">
      <h2>
        Results
        <span className="results-header-actions">
          <span className="meta">{metaText}</span>
          <RowsPerPageSelector value={rowsPerPage} onChange={onRowsPerPageChange} />
          {hasCustomWidths && (
            <button
              type="button"
              className="column-selector-btn"
              onClick={handleResetColumnWidths}
              title="Reset all column widths to defaults"
            >
              Reset widths
            </button>
          )}
          <ColumnSelector
            columns={config.columns}
            hiddenKeys={hiddenSet}
            onToggle={(k) => onToggleColumn(config.entityKey, k)}
          />
        </span>
      </h2>

      {error && <div className="error">{error}</div>}

      <ResultsTable
        columns={visibleColumns}
        data={paginatedData.data}
        keyExtractor={(row) => config.keyExtractor(row as { id: number })}
        emptyMessage={config.emptyMessage}
        sortableColumnKeys={columnSortConfig.sortableColumnKeys}
        sortColumnKey={columnSortConfig.sortColumnKey}
        sortOrder={order ?? null}
        onColumnSort={onColumnSort}
        columnWidths={columnWidths}
        onColumnResize={handleColumnResize}
      />

      {paginatedData.totalPages > 1 && (
        <div className="pagination">
          <button
            type="button"
            className="pagination-btn"
            disabled={paginatedData.page === 0}
            onClick={() => onPageChange(Math.max(0, currentPage - 1))}
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
            onClick={() => onPageChange(Math.min(paginatedData.totalPages - 1, currentPage + 1))}
          >
            Next
          </button>
        </div>
      )}
    </section>
  );
}

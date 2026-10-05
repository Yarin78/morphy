import { type KeyboardEvent, type PointerEvent, type ReactNode, useEffect, useRef } from 'react';
import { TbChevronDown, TbChevronRight, TbSearch, TbX } from 'react-icons/tb';
import { useDatabaseView } from '../databaseStore';
import { defaultOrderOf, entityLabel, NOTATION_COLUMN, sortFieldOf } from '../../search/columns';
import { resetColumns, setColumnWidth, shownColumnKeys, toggleColumn, useResultColumns } from '../../search/columnLayout';
import {
  type EntityForm,
  type GameForm,
  hasAdvancedFilters,
  isValidDate,
  isValidRating,
  nameLabel,
  RATING_MODES,
  type RatingMode,
  RESULTS,
  SEARCH_KIND_LABELS,
  SEARCH_KINDS,
  type SearchKind,
  TIME_CONTROLS,
  type TimeControl,
} from '../../search/queries';
import { queryOf } from '../../search/useDatabaseSearch';
import { ColumnPicker } from './ColumnPicker';

// How near the end of the results, in rows, the next page is fetched
const PREFETCH_ROWS = 20;

/** A search's duration: "12 ms", or "1.4 s" from a second. */
function formatDuration(ms: number): string {
  return ms < 1000 ? `${Math.round(ms)} ms` : `${(ms / 1000).toFixed(1)} s`;
}

// The narrowest a column can be made
const MIN_COLUMN_WIDTH = 32;

/**
 * A database's search: games by default, or players, tournaments and the other entities. A form
 * for the common filters, more of them a click away, or the query typed in full; the results
 * below, the one picked shown in the preview.
 */
export function SearchPane() {
  const { search } = useDatabaseView();
  const s = search.current;
  return (
    <div className="search-pane">
      <div className="search-kinds" role="tablist">
        {SEARCH_KINDS.map((k) => (
          <button
            key={k}
            role="tab"
            aria-selected={search.kind === k}
            className={`search-kind${search.kind === k ? ' active' : ''}`}
            onClick={() => search.setKind(k)}
          >
            {SEARCH_KIND_LABELS[k]}
          </button>
        ))}
      </div>
      <form
        className="search-form"
        onSubmit={(e) => {
          e.preventDefault();
          search.run();
        }}
      >
        {s.mode === 'query' ? <QueryForm /> : search.kind === 'games' ? <GameFormFields /> : <EntityFormFields kind={search.kind} />}
        <div className="search-actions">
          <button type="submit" className="search-button primary" disabled={s.results?.loading && s.results.rows.length === 0}>
            <TbSearch /> Search
          </button>
          <button type="button" className="search-button" onClick={search.reset}>
            Clear
          </button>
          <span className="search-actions-gap" />
          {s.mode === 'form' ? (
            <button
              type="button"
              className="search-link"
              title="Edit the search as a query in the search language"
              onClick={() => search.update({ mode: 'query', queryText: queryOf(search.kind, s) })}
            >
              Edit as query
            </button>
          ) : (
            <button type="button" className="search-link" onClick={() => search.update({ mode: 'form' })}>
              Use the form
            </button>
          )}
        </div>
        {s.mode === 'form' && <QueryPreview query={queryOf(search.kind, s)} />}
      </form>
      <Results />
    </div>
  );
}

function QueryPreview({ query }: { query: string }) {
  return (
    <div className="search-query-preview" title="The search, in the search language">
      {query ? <code>{query}</code> : <span className="pane-muted">Everything</span>}
    </div>
  );
}

function Field({ label, children, wide }: { label: string; children: ReactNode; wide?: boolean }) {
  return (
    <label className={`search-field${wide ? ' wide' : ''}`}>
      <span className="search-label">{label}</span>
      {children}
    </label>
  );
}

function TextInput({
  value,
  onChange,
  placeholder,
  invalid,
  autoFocus,
}: {
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  invalid?: boolean;
  autoFocus?: boolean;
}) {
  return (
    <input
      type="text"
      className={`search-input${invalid ? ' invalid' : ''}`}
      value={value}
      placeholder={placeholder}
      spellCheck={false}
      autoFocus={autoFocus}
      onChange={(e) => onChange(e.target.value)}
    />
  );
}

function DateRange({
  from,
  to,
  onChange,
}: {
  from: string;
  to: string;
  onChange: (changes: { dateFrom?: string; dateTo?: string }) => void;
}) {
  return (
    <div className="search-range" title="A year, a month (1990-05) or a day (1990-05-17); either end can be left open">
      <TextInput value={from} onChange={(v) => onChange({ dateFrom: v })} placeholder="From" invalid={!isValidDate(from)} />
      <span className="search-range-dash">–</span>
      <TextInput value={to} onChange={(v) => onChange({ dateTo: v })} placeholder="To" invalid={!isValidDate(to)} />
    </div>
  );
}

function TimeControls({ value, onChange }: { value: TimeControl[]; onChange: (value: TimeControl[]) => void }) {
  return (
    <div className="search-chips" title="The time control of the event; none picked is any">
      {TIME_CONTROLS.map((tc) => {
        const on = value.includes(tc.id);
        return (
          <button
            type="button"
            key={tc.id}
            className={`search-chip${on ? ' on' : ''}`}
            aria-pressed={on}
            onClick={() => onChange(on ? value.filter((v) => v !== tc.id) : [...value, tc.id])}
          >
            {tc.label}
          </button>
        );
      })}
    </div>
  );
}

function GameFormFields() {
  const { search } = useDatabaseView();
  const s = search.current;
  const form = s.gameForm;
  const set = (changes: Partial<GameForm>) => search.updateGameForm(changes);
  const advancedSet = hasAdvancedFilters(form) || !!form.entity;
  return (
    <>
      <div className="search-row">
        <Field label={form.eitherColour ? 'Player' : 'White'}>
          <TextInput value={form.white} onChange={(v) => set({ white: v })} placeholder="Last name" autoFocus />
        </Field>
        <Field label={form.eitherColour ? 'Opponent' : 'Black'}>
          <TextInput value={form.black} onChange={(v) => set({ black: v })} placeholder="Last name" />
        </Field>
      </div>
      <label className="search-check">
        <input type="checkbox" checked={form.eitherColour} onChange={(e) => set({ eitherColour: e.target.checked })} />
        Either colour
      </label>
      <div className="search-row">
        <Field label="Date">
          <DateRange from={form.dateFrom} to={form.dateTo} onChange={set} />
        </Field>
        <Field label="Time control">
          <TimeControls value={form.timeControls} onChange={(v) => set({ timeControls: v })} />
        </Field>
      </div>
      <button
        type="button"
        className="search-disclosure"
        aria-expanded={s.advancedOpen}
        onClick={() => search.update({ advancedOpen: !s.advancedOpen })}
      >
        {s.advancedOpen ? <TbChevronDown /> : <TbChevronRight />}
        More filters
        {advancedSet && !s.advancedOpen && <span className="search-dot" title="Some are set" />}
      </button>
      {s.advancedOpen && (
        <div className="search-advanced">
          {form.entity && (
            <div className="search-entity">
              <span>{form.entity.label}</span>
              <button type="button" title="Don't limit the games to this" onClick={() => set({ entity: null })}>
                <TbX />
              </button>
            </div>
          )}
          <div className="search-row">
            <Field label="Result">
              <div className="search-segments">
                {RESULTS.map((r) => (
                  <button
                    type="button"
                    key={r.id}
                    className={`search-segment${form.result === r.id ? ' on' : ''}`}
                    onClick={() => set({ result: r.id })}
                  >
                    {r.label}
                  </button>
                ))}
              </div>
            </Field>
            <Field label="ECO">
              <TextInput value={form.eco} onChange={(v) => set({ eco: v })} placeholder="B90, B9*" />
            </Field>
          </div>
          <div className="search-row">
            <Field label="Rating" wide>
              <div className="search-range">
                <TextInput value={form.ratingMin} onChange={(v) => set({ ratingMin: v })} placeholder="Min" invalid={!isValidRating(form.ratingMin)} />
                <span className="search-range-dash">–</span>
                <TextInput value={form.ratingMax} onChange={(v) => set({ ratingMax: v })} placeholder="Max" invalid={!isValidRating(form.ratingMax)} />
                <select
                  className="search-input search-select"
                  value={form.ratingMode}
                  onChange={(e) => set({ ratingMode: e.target.value as RatingMode })}
                >
                  {RATING_MODES.map((m) => (
                    <option key={m.id} value={m.id}>
                      {m.label}
                    </option>
                  ))}
                </select>
              </div>
            </Field>
          </div>
          <div className="search-row">
            <Field label="Event">
              <TextInput value={form.tournament} onChange={(v) => set({ tournament: v })} />
            </Field>
            <Field label="Annotator">
              <TextInput value={form.annotator} onChange={(v) => set({ annotator: v })} />
            </Field>
            <Field label="Source">
              <TextInput value={form.source} onChange={(v) => set({ source: v })} />
            </Field>
          </div>
        </div>
      )}
    </>
  );
}

function EntityFormFields({ kind }: { kind: Exclude<SearchKind, 'games'> }) {
  const { search } = useDatabaseView();
  const form = search.current.entityForm;
  const set = (changes: Partial<EntityForm>) => search.updateEntityForm(changes);
  return (
    <>
      <div className="search-row">
        <Field label={nameLabel(kind)} wide>
          <TextInput
            value={form.name}
            onChange={(v) => set({ name: v })}
            placeholder={kind === 'players' ? 'Last name, first name' : 'Starts with'}
            autoFocus
          />
        </Field>
      </div>
      {kind === 'tournaments' && (
        <>
          <div className="search-row">
            <Field label="Place">
              <TextInput value={form.place} onChange={(v) => set({ place: v })} />
            </Field>
            <Field label="Date">
              <DateRange from={form.dateFrom} to={form.dateTo} onChange={set} />
            </Field>
          </div>
          <div className="search-row">
            <Field label="Time control">
              <TimeControls value={form.timeControls} onChange={(v) => set({ timeControls: v })} />
            </Field>
          </div>
        </>
      )}
    </>
  );
}

function QueryForm() {
  const { search } = useDatabaseView();
  const s = search.current;
  return (
    <div className="search-query">
      <textarea
        className="search-input search-query-text"
        value={s.queryText}
        spellCheck={false}
        autoFocus
        // Typing goes on from the end of the query made by the form
        onFocus={(e) => e.currentTarget.setSelectionRange(e.currentTarget.value.length, e.currentTarget.value.length)}
        rows={3}
        placeholder={search.kind === 'games' ? 'e.g. white:Carlsen result:1-0 date:2015.. rating:2700..' : 'e.g. Kasp'}
        onChange={(e) => search.update({ queryText: e.target.value })}
        onKeyDown={(e) => {
          // Enter searches; Shift+Enter starts a new line
          if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            search.run();
          }
        }}
      />
      <div className="search-query-help">
        Conditions are <code>field:value</code>, all of which must hold. See the search guide for the fields.
      </div>
    </div>
  );
}

function Results() {
  const { search, openGame, previewKeys } = useDatabaseView();
  const kind = search.kind;
  const s = search.current;
  const results = s.results;
  const resultColumns = useResultColumns(kind);
  const columns = resultColumns.shown;
  const listRef = useRef<HTMLDivElement>(null);
  const rows = results?.rows ?? [];

  // The row picked stays in sight as it's moved with the keys
  useEffect(() => {
    if (s.selected == null) return;
    const row = listRef.current?.querySelector(`[data-index="${s.selected}"]`);
    row?.scrollIntoView({ block: 'nearest' });
  }, [s.selected, kind]);

  const open = (index: number) => {
    const row = rows[index] as { id: number } & Record<string, unknown>;
    if (!row) return;
    if (kind === 'games') openGame(row.id);
    else search.showGamesOf(kind, row.id, entityLabel(kind, row));
  };

  const pick = (index: number) => {
    const next = Math.max(0, Math.min(rows.length - 1, index));
    if (rows.length === 0) return;
    search.select(next);
    if (next >= rows.length - PREFETCH_ROWS) search.loadMore();
  };

  const onKeyDown = (e: KeyboardEvent<HTMLDivElement>) => {
    const current = s.selected ?? -1;
    const page = Math.max(1, Math.floor((listRef.current?.clientHeight ?? 300) / 24) - 1);
    switch (e.key) {
      case 'ArrowDown':
        pick(current + 1);
        break;
      case 'ArrowUp':
        pick(current - 1);
        break;
      case 'PageDown':
        pick(current + page);
        break;
      case 'PageUp':
        pick(current - page);
        break;
      case 'Enter':
        if (s.selected != null) open(s.selected);
        break;
      default:
        // Left and right move through the game previewed
        if (kind === 'games') previewKeys(e);
        return;
    }
    e.preventDefault();
  };

  const onScroll = () => {
    const el = listRef.current;
    if (el && el.scrollTop + el.clientHeight > el.scrollHeight - 200) search.loadMore();
  };

  // The games are fetched again when their moves are to be shown, as they come with the moves
  const refetchIfMovesShown = (wasShown: boolean) => {
    if (kind === 'games' && !wasShown && shownColumnKeys(kind).includes(NOTATION_COLUMN)) search.run();
  };

  const onToggleColumn = (key: string) => {
    const wasShown = shownColumnKeys(kind).includes(NOTATION_COLUMN);
    toggleColumn(kind, key);
    refetchIfMovesShown(wasShown);
  };

  const onResetColumns = () => {
    const wasShown = shownColumnKeys(kind).includes(NOTATION_COLUMN);
    resetColumns(kind);
    refetchIfMovesShown(wasShown);
  };

  // A column is made wider or narrower by dragging the right edge of its header
  const startResize = (e: PointerEvent<HTMLDivElement>, key: string, width: number) => {
    e.preventDefault();
    e.stopPropagation();
    const handle = e.currentTarget;
    const startX = e.clientX;
    handle.setPointerCapture(e.pointerId);
    const onMove = (ev: globalThis.PointerEvent) =>
      setColumnWidth(kind, key, Math.max(MIN_COLUMN_WIDTH, Math.round(width + ev.clientX - startX)));
    const onUp = () => {
      handle.removeEventListener('pointermove', onMove);
      handle.removeEventListener('pointerup', onUp);
      handle.removeEventListener('pointercancel', onUp);
    };
    handle.addEventListener('pointermove', onMove);
    handle.addEventListener('pointerup', onUp);
    handle.addEventListener('pointercancel', onUp);
  };

  const sortField = s.sort?.field;
  const label = SEARCH_KIND_LABELS[kind].toLowerCase();
  const count = results
    ? results.total != null
      ? `${results.total.toLocaleString()} ${results.total === 1 ? label.replace(/s$/, '') : label}`
      : `${rows.length.toLocaleString()}${results.complete ? '' : '+'} ${label}`
    : '';

  return (
    <div className="search-results">
      <div className="search-results-head">
        <span>
          {results?.loading && rows.length === 0 ? 'Searching…' : count}
          {results?.durationMs != null && !(results.loading && rows.length === 0) && (
            <span
              className="search-duration"
              title={`${results.query || '(everything)'}\nSorted by ${results.sortBy}`}
            >
              {' · '}
              {formatDuration(results.durationMs)}
            </span>
          )}
        </span>
        <ColumnPicker columns={resultColumns} onToggle={onToggleColumn} onReset={onResetColumns} />
      </div>
      {results?.error && <div className="search-error">{results.error}</div>}
      <div className="search-results-list" ref={listRef} tabIndex={0} onKeyDown={onKeyDown} onScroll={onScroll}>
        <table className="search-table" style={{ minWidth: columns.reduce((sum, c) => sum + c.width, 0) }}>
          <colgroup>
            {columns.map((c) => (
              <col key={c.key} style={{ width: c.width }} />
            ))}
            {/* Takes the width the columns leave, so they keep theirs */}
            <col />
          </colgroup>
          <thead>
            <tr>
              {columns.map((c) => {
                const field = sortFieldOf(kind, c.key);
                const sorted = field && field === sortField;
                return (
                  <th key={c.key} className={field ? 'sortable' : undefined}>
                    {/* The sort is on the label, so that letting go of a dragged edge doesn't sort */}
                    <span
                      className="search-th-label"
                      title={field ? `Sort by ${c.label}` : c.label}
                      onClick={field ? () => search.sortBy(field, defaultOrderOf(field)) : undefined}
                    >
                      {c.label}
                      {sorted && <span className="search-sort">{s.sort!.order === 'desc' ? ' ↓' : ' ↑'}</span>}
                    </span>
                    <div className="search-th-resize" onPointerDown={(e) => startResize(e, c.key, c.width)} />
                  </th>
                );
              })}
              <th />
            </tr>
          </thead>
          <tbody className={results?.loading && rows.length === 0 ? 'loading' : undefined}>
            {rows.map((row, i) => (
              <tr
                key={`${row.id}:${i}`}
                data-index={i}
                className={`${i === s.selected ? 'selected' : ''}${(row as { deleted?: boolean }).deleted ? ' deleted' : ''}`}
                onClick={() => pick(i)}
                onDoubleClick={() => open(i)}
                title={kind === 'games' ? 'Double-click to open on a board' : 'Double-click to show the games'}
              >
                {columns.map((c) => (
                  <td key={c.key}>{c.render(row)}</td>
                ))}
                <td />
              </tr>
            ))}
          </tbody>
        </table>
        {results && !results.loading && rows.length === 0 && !results.error && (
          <div className="search-results-empty">Nothing found.</div>
        )}
        {results?.loading && rows.length > 0 && <div className="search-results-more">Loading more…</div>}
      </div>
    </div>
  );
}

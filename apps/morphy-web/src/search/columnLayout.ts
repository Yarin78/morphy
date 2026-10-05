import { useSyncExternalStore } from 'react';
import { columnsOf, defaultColumnsOf, type Column } from './columns';
import type { SearchKind } from './queries';

// The columns of the search results of each kind: which are shown and how wide they've been made,
// the same in every database. Kept in localStorage; a kind not changed has its default columns.

export interface KindColumns {
  /** The keys of the columns shown, if others than the default ones have been picked */
  shown?: string[];
  /** The widths the columns have been made, by key */
  widths: Record<string, number>;
}

type ColumnLayout = Partial<Record<SearchKind, KindColumns>>;

const STORAGE_KEY = 'morphy-search-columns';
const NOT_CHANGED: KindColumns = { widths: {} };

function load(): ColumnLayout {
  try {
    return (JSON.parse(localStorage.getItem(STORAGE_KEY) ?? 'null') as ColumnLayout | null) ?? {};
  } catch {
    return {};
  }
}

let layout = load();
const listeners = new Set<() => void>();

function subscribe(listener: () => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

function getLayout(): ColumnLayout {
  return layout;
}

function update(kind: SearchKind, change: (c: KindColumns) => KindColumns) {
  layout = { ...layout, [kind]: change(layout[kind] ?? NOT_CHANGED) };
  listeners.forEach((l) => l());
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(layout));
  } catch {
    // ignore, keeping the columns is a convenience
  }
}

/** The keys of the columns of a kind's results that are shown. */
export function shownColumnKeys(kind: SearchKind): string[] {
  return layout[kind]?.shown ?? defaultColumnsOf(kind);
}

export interface ResultColumns {
  /** All the columns of the kind, with the widths they've been made */
  all: Column[];
  /** The columns shown, in their order */
  shown: Column[];
  /** Whether the columns shown are the default ones, at their default widths */
  isDefault: boolean;
}

/** The columns of a kind's results, as they've been picked and sized. */
export function useResultColumns(kind: SearchKind): ResultColumns {
  const kindColumns = useSyncExternalStore(subscribe, getLayout)[kind] ?? NOT_CHANGED;
  const all = columnsOf(kind).map((c) => ({ ...c, width: kindColumns.widths[c.key] ?? c.width }));
  const shownKeys = new Set(kindColumns.shown ?? defaultColumnsOf(kind));
  return {
    all,
    shown: all.filter((c) => shownKeys.has(c.key)),
    isDefault: !kindColumns.shown && Object.keys(kindColumns.widths).length === 0,
  };
}

/** Shows a column of a kind's results, or hides it. */
export function toggleColumn(kind: SearchKind, key: string) {
  const shown = shownColumnKeys(kind);
  const next = shown.includes(key) ? shown.filter((k) => k !== key) : [...shown, key];
  update(kind, (c) => ({ ...c, shown: next }));
}

export function setColumnWidth(kind: SearchKind, key: string, width: number) {
  update(kind, (c) => ({ ...c, widths: { ...c.widths, [key]: width } }));
}

/** Puts back the default columns of a kind's results, at their default widths. */
export function resetColumns(kind: SearchKind) {
  update(kind, () => NOT_CHANGED);
}

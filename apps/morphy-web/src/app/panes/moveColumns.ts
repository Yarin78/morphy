import { useSyncExternalStore } from 'react';

// The columns shown of the moves from a position, the same in every board. Kept in localStorage;
// until they're picked, the default ones.

/** The columns that can be shown beside a move, in their order. */
export const MOVE_COLUMNS = [
  { key: 'games', label: 'Games' },
  { key: 'score', label: 'Score' },
  { key: 'draws', label: 'Draw %' },
  { key: 'delta', label: 'Δ%' },
  { key: 'average', label: 'Avg' },
  { key: 'hot', label: 'Hot' },
  { key: 'last', label: 'Last' },
  { key: 'players', label: 'Top players' },
] as const;

export type MoveColumnKey = (typeof MOVE_COLUMNS)[number]['key'];

const DEFAULT_COLUMNS: MoveColumnKey[] = ['games', 'score', 'average', 'hot', 'players'];

const STORAGE_KEY = 'morphy-position-move-columns';

function load(): MoveColumnKey[] | null {
  try {
    const saved = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? 'null') as unknown;
    return Array.isArray(saved) ? MOVE_COLUMNS.map((c) => c.key).filter((key) => saved.includes(key)) : null;
  } catch {
    return null;
  }
}

/** The columns picked, null for the default ones. */
let picked = load();
const listeners = new Set<() => void>();

function subscribe(listener: () => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

function set(next: MoveColumnKey[] | null) {
  picked = next;
  listeners.forEach((l) => l());
  try {
    if (next) localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
    else localStorage.removeItem(STORAGE_KEY);
  } catch {
    // ignore, keeping the columns is a convenience
  }
}

/** The columns shown beside the moves, in their order, and whether they're the default ones. */
export function useMoveColumns(): { shown: MoveColumnKey[]; isDefault: boolean } {
  const current = useSyncExternalStore(subscribe, () => picked);
  return { shown: current ?? DEFAULT_COLUMNS, isDefault: current === null };
}

/** Shows a column beside the moves, or hides it. */
export function toggleMoveColumn(key: string) {
  const shown = picked ?? DEFAULT_COLUMNS;
  set(MOVE_COLUMNS.map((c) => c.key).filter((k) => (k === key ? !shown.includes(k) : shown.includes(k))));
}

export function resetMoveColumns() {
  set(null);
}

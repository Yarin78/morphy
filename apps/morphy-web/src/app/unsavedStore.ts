// Which boards have changes not yet saved, and how to save each, for the navigator to show and
// for closing a board to ask about. It isn't kept with the documents: unsaved changes don't
// survive a reload.

export type SaveMode = 'save' | 'saveAs';

export interface BoardSaver {
  /** Whether the board can be saved where it is; a game in a read-only database can't */
  canSave: boolean;
  /** Saves the board, picking a database first if it needs one; whether it was saved */
  save: (mode: SaveMode) => Promise<boolean>;
}

let unsaved: ReadonlySet<string> = new Set();
const listeners = new Set<() => void>();
const savers = new Map<string, BoardSaver>();

export function subscribeUnsaved(listener: () => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

/** The ids of the boards with unsaved changes */
export function getUnsaved(): ReadonlySet<string> {
  return unsaved;
}

export function setUnsaved(id: string, hasChanges: boolean) {
  if (unsaved.has(id) === hasChanges) return;
  const next = new Set(unsaved);
  if (hasChanges) next.add(id);
  else next.delete(id);
  unsaved = next;
  listeners.forEach((l) => l());
}

export function registerSaver(id: string, saver: BoardSaver | null) {
  if (saver) savers.set(id, saver);
  else savers.delete(id);
}

export function saverOf(id: string): BoardSaver | undefined {
  return savers.get(id);
}

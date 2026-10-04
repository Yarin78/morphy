import type { GameTree, TreeSnapshot } from 'game-view';
import { showError } from './errorStore';

// The unsaved changes of each board, kept in localStorage so that a reload or a crash doesn't
// lose them: the board puts them back when it opens again. A draft is kept while its board has
// unsaved changes, and removed once it hasn't, or is closed.

const KEY_PREFIX = 'morphy-draft:';

export interface Draft {
  /** The game the changes are to, so changes aren't put on another game */
  databaseId: string | null;
  gameId: number | null;
  /** When the draft was last written, as an ISO-8601 instant */
  savedAt: string;
  /** A fingerprint of the game as saved when the changes were made, to tell if it's changed since */
  base: string;
  /** The game with the changes: its moves and tags, as gameContent gives them */
  content: string;
  /** The move shown, as an index in the order of the movetext, or null for the start */
  current: number | null;
}

/** A game's moves and tags, to compare and keep. */
export function gameContent(game: GameTree): string {
  return JSON.stringify({ moves: game.toMoves(), tags: game.tagValues() });
}

/** Makes a game's moves and tags those of gameContent, and shows a move. */
export function applyContent(game: GameTree, content: string, current: number | null) {
  const { moves, tags } = JSON.parse(content) as { moves: TreeSnapshot['moves']; tags: Record<string, string> };
  game.restore({ moves, current });
  for (const name of Object.keys(game.tagValues())) {
    if (!(name in tags)) game.setTag(name, '');
  }
  for (const [name, value] of Object.entries(tags)) game.setTag(name, value);
}

/** A short fingerprint of a text (djb2). */
export function fingerprint(text: string): string {
  let hash = 5381;
  for (let i = 0; i < text.length; i++) hash = ((hash << 5) + hash + text.charCodeAt(i)) | 0;
  return (hash >>> 0).toString(36) + text.length.toString(36);
}

export function loadDraft(boardId: string): Draft | null {
  try {
    const raw = localStorage.getItem(KEY_PREFIX + boardId);
    return raw ? (JSON.parse(raw) as Draft) : null;
  } catch {
    return null;
  }
}

// Running out of room is told once, not on every change after
let toldNoRoom = false;

export function saveDraft(boardId: string, draft: Draft) {
  try {
    localStorage.setItem(KEY_PREFIX + boardId, JSON.stringify(draft));
    toldNoRoom = false;
  } catch (err) {
    if (toldNoRoom) return;
    toldNoRoom = true;
    showError(
      'Keeping the unsaved changes failed',
      new Error(
        `The browser has no room left for them (${err instanceof Error ? err.message : String(err)}). ` +
          'They are still on the board, but would be lost if the page were reloaded: save them, or close some boards.'
      )
    );
  }
}

export function deleteDraft(boardId: string) {
  try {
    localStorage.removeItem(KEY_PREFIX + boardId);
  } catch {
    // ignore
  }
}

/** Removes the drafts of boards that aren't open, as of a board closed before its draft was. */
export function deleteDraftsExcept(boardIds: Iterable<string>) {
  const keep = new Set([...boardIds].map((id) => KEY_PREFIX + id));
  try {
    for (let i = localStorage.length - 1; i >= 0; i--) {
      const key = localStorage.key(i);
      if (key?.startsWith(KEY_PREFIX) && !keep.has(key)) localStorage.removeItem(key);
    }
  } catch {
    // ignore
  }
}

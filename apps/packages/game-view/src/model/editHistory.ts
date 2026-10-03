import type { TreeSnapshot } from './GameTree';

// The edits that can be undone at most
const MAX_EDITS = 200;

/** The edits made to the moves of a game, to undo and redo: the moves before each one. */
export class EditHistory {
  private readonly undos: TreeSnapshot[] = [];
  private readonly redos: TreeSnapshot[] = [];

  /** An edit was made, to the moves of the snapshot; the edits undone can't be redone any more. */
  record(before: TreeSnapshot) {
    this.undos.push(before);
    if (this.undos.length > MAX_EDITS) this.undos.shift();
    this.redos.length = 0;
  }

  get canUndo(): boolean {
    return this.undos.length > 0;
  }

  get canRedo(): boolean {
    return this.redos.length > 0;
  }

  /**
   * The moves before the last edit, to go back to, or undefined if there's none.
   *
   * @param current the moves now, to redo the edit by
   */
  undo(current: TreeSnapshot): TreeSnapshot | undefined {
    const before = this.undos.pop();
    if (before) this.redos.push(current);
    return before;
  }

  /**
   * The moves after the last edit undone, to go to again, or undefined if there's none.
   *
   * @param current the moves now, to undo it again by
   */
  redo(current: TreeSnapshot): TreeSnapshot | undefined {
    const after = this.redos.pop();
    if (after) this.undos.push(current);
    return after;
  }
}

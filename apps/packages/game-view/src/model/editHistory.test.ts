import { describe, expect, it } from 'vitest';
import { EditHistory } from './editHistory';
import type { TreeSnapshot } from './GameTree';

const snap = (pgn: string): TreeSnapshot => ({ moves: { pgn, annotations: [] }, current: null });

describe('the edit history', () => {
  it('undoes the edits from the last, and redoes them', () => {
    const history = new EditHistory();
    expect(history.canUndo).toBe(false);
    history.record(snap('a'));
    history.record(snap('b'));
    expect(history.undo(snap('c'))).toEqual(snap('b'));
    expect(history.undo(snap('b'))).toEqual(snap('a'));
    expect(history.undo(snap('a'))).toBeUndefined();
    expect(history.redo(snap('a'))).toEqual(snap('b'));
    expect(history.redo(snap('b'))).toEqual(snap('c'));
    expect(history.canRedo).toBe(false);
  });

  it('forgets the edits undone when another edit is made', () => {
    const history = new EditHistory();
    history.record(snap('a'));
    history.undo(snap('b'));
    history.record(snap('a'));
    expect(history.canRedo).toBe(false);
  });

  it('keeps the last 200 edits', () => {
    const history = new EditHistory();
    for (let i = 0; i < 250; i++) history.record(snap(String(i)));
    let last: TreeSnapshot | undefined;
    let count = 0;
    for (let s; (s = history.undo(snap('x'))); count++) last = s;
    expect(count).toBe(200);
    expect(last).toEqual(snap('50'));
  });
});

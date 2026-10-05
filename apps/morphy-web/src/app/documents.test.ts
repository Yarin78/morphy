import { describe, expect, it } from 'vitest';
import { adjacentDocument, documentsReducer, type DocumentsState, type MorphyDocument } from './documents';

const documents: MorphyDocument[] = [
  { kind: 'home', id: 'home' },
  { kind: 'board', id: 'b1', number: 1 },
  { kind: 'logs', id: 'logs' },
  { kind: 'database', id: 'd1', databaseId: 'x', name: 'X' },
  { kind: 'database', id: 'd2', databaseId: 'y', name: 'Y' },
];

const at = (activeId: string): DocumentsState => ({ documents, activeId });

describe('adjacentDocument', () => {
  it('goes through the documents in the order of the navigator', () => {
    const order = ['home'];
    for (let i = 0; i < 4; i++) order.push(adjacentDocument(at(order[order.length - 1]), 1));
    expect(order).toEqual(['home', 'd1', 'd2', 'b1', 'home']);
  });

  it('goes round from the ends, passing Logs over', () => {
    expect(adjacentDocument(at('b1'), 1)).toBe('home');
    expect(adjacentDocument(at('home'), -1)).toBe('b1');
  });

  it('goes back into the round from Logs', () => {
    expect(adjacentDocument(at('logs'), 1)).toBe('home');
    expect(adjacentDocument(at('logs'), -1)).toBe('b1');
  });
});

describe('moving a document', () => {
  const ids = (state: DocumentsState) => state.documents.map((d) => d.id);

  it('moves a database before or after another', () => {
    expect(ids(documentsReducer(at('home'), { type: 'move', id: 'd2', targetId: 'd1', after: false }))).toEqual([
      'home',
      'b1',
      'logs',
      'd2',
      'd1',
    ]);
    expect(ids(documentsReducer(at('home'), { type: 'move', id: 'd1', targetId: 'd2', after: true }))).toEqual([
      'home',
      'b1',
      'logs',
      'd2',
      'd1',
    ]);
  });

  it('keeps a document within its section', () => {
    const state = at('home');
    expect(documentsReducer(state, { type: 'move', id: 'd1', targetId: 'b1', after: false })).toBe(state);
  });

  it('changes the order the documents are gone through in', () => {
    const moved = documentsReducer(at('d2'), { type: 'move', id: 'd2', targetId: 'd1', after: false });
    expect(adjacentDocument(moved, 1)).toBe('d1');
  });
});

describe('closing a document', () => {
  it('shows the one above it in the navigator', () => {
    expect(documentsReducer(at('d2'), { type: 'close', id: 'd2' }).activeId).toBe('d1');
    // The first board: the last database is above it, though Logs was opened before it
    expect(documentsReducer(at('b1'), { type: 'close', id: 'b1' }).activeId).toBe('d2');
  });

  it('never shows Logs', () => {
    const state: DocumentsState = {
      // Logs opened just before the board, which used to be shown when it was closed
      documents: [
        { kind: 'home', id: 'home' },
        { kind: 'logs', id: 'logs' },
        { kind: 'board', id: 'b1', number: 1 },
      ],
      activeId: 'b1',
    };
    expect(documentsReducer(state, { type: 'close', id: 'b1' }).activeId).toBe('home');
  });
});

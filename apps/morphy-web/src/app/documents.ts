import type { AddPanelPositionOptions, DockviewApi } from 'dockview-react';

// The documents of the app. Each one has its own Dockview grid, which fills the screen when it
// is the active one. Home, All Databases, Logs and Settings are singletons; databases and boards can
// be opened any number of times.

export type SingletonKind = 'home' | 'databases' | 'settings' | 'logs';

/** A position in a quoted game to open the board at; see quotedPosition in game-view. */
export interface QuoteTarget {
  fen: string;
  index: number;
}

export type MorphyDocument =
  | { kind: SingletonKind; id: SingletonKind }
  | { kind: 'database'; id: string; databaseId: string; name: string }
  | {
      kind: 'board';
      id: string;
      /** The N of "Board N", the title until the game has players */
      number: number;
      databaseId?: string;
      gameId?: number;
      quote?: QuoteTarget;
      /** "White vs Black", once the game is loaded */
      title?: string;
    };

export type BoardDocument = Extract<MorphyDocument, { kind: 'board' }>;
export type DatabaseDocument = Extract<MorphyDocument, { kind: 'database' }>;

export interface DocumentsState {
  documents: MorphyDocument[];
  activeId: string;
}

export const INITIAL_STATE: DocumentsState = { documents: [{ kind: 'home', id: 'home' }], activeId: 'home' };

export type DocumentsAction =
  | { type: 'activate'; id: string }
  | { type: 'openSingleton'; kind: SingletonKind }
  | { type: 'openDatabase'; databaseId: string; name: string }
  | { type: 'openBoard'; databaseId?: string; gameId?: number; quote?: QuoteTarget }
  | { type: 'close'; id: string }
  | { type: 'closeAllBoards' }
  | { type: 'updateBoard'; id: string; changes: Partial<Pick<BoardDocument, 'title' | 'databaseId' | 'gameId'>> };

export function documentTitle(doc: MorphyDocument): string {
  switch (doc.kind) {
    case 'home':
      return 'Home';
    case 'databases':
      return 'All Databases';
    case 'settings':
      return 'Settings';
    case 'logs':
      return 'Logs';
    case 'database':
      return doc.name;
    case 'board':
      return doc.title ?? `Board ${doc.number}`;
  }
}

/** The lowest board number no open board uses. */
function freeBoardNumber(documents: MorphyDocument[]): number {
  const used = new Set(documents.flatMap((d) => (d.kind === 'board' ? [d.number] : [])));
  let n = 1;
  while (used.has(n)) n++;
  return n;
}

function newId(prefix: string): string {
  return `${prefix}:${Math.random().toString(36).slice(2, 8)}`;
}

/** The documents without these, and the one to show if the active one is among them. */
function without(state: DocumentsState, removed: (doc: MorphyDocument) => boolean): DocumentsState {
  const index = state.documents.findIndex((d) => d.id === state.activeId);
  const documents = state.documents.filter((d) => !removed(d));
  if (documents.some((d) => d.id === state.activeId)) return { ...state, documents };
  // The nearest remaining document before the closed one, else Home
  const before = state.documents.slice(0, index).reverse().find((d) => !removed(d));
  if (before) return { documents, activeId: before.id };
  const home = documents.find((d) => d.kind === 'home');
  return home ? { documents, activeId: home.id } : { documents: [...documents, INITIAL_STATE.documents[0]], activeId: 'home' };
}

export function documentsReducer(state: DocumentsState, action: DocumentsAction): DocumentsState {
  switch (action.type) {
    case 'activate':
      return state.documents.some((d) => d.id === action.id) ? { ...state, activeId: action.id } : state;
    case 'openSingleton': {
      const documents = state.documents.some((d) => d.id === action.kind)
        ? state.documents
        : [...state.documents, { kind: action.kind, id: action.kind }];
      return { documents, activeId: action.kind };
    }
    case 'openDatabase': {
      const open = state.documents.find((d) => d.kind === 'database' && d.databaseId === action.databaseId);
      if (open) return { ...state, activeId: open.id };
      const doc: MorphyDocument = { kind: 'database', id: newId('db'), databaseId: action.databaseId, name: action.name };
      return { documents: [...state.documents, doc], activeId: doc.id };
    }
    case 'openBoard': {
      // A game that is already open is shown, not opened again (unless at another position)
      if (action.databaseId && action.gameId != null && !action.quote) {
        const open = state.documents.find(
          (d) => d.kind === 'board' && d.databaseId === action.databaseId && d.gameId === action.gameId
        );
        if (open) return { ...state, activeId: open.id };
      }
      const doc: MorphyDocument = {
        kind: 'board',
        id: newId('board'),
        number: freeBoardNumber(state.documents),
        databaseId: action.databaseId,
        gameId: action.gameId,
        quote: action.quote,
      };
      return { documents: [...state.documents, doc], activeId: doc.id };
    }
    case 'close':
      return without(state, (d) => d.id === action.id);
    case 'closeAllBoards':
      return without(state, (d) => d.kind === 'board');
    case 'updateBoard':
      return {
        ...state,
        documents: state.documents.map((d) => (d.id === action.id && d.kind === 'board' ? { ...d, ...action.changes } : d)),
      };
  }
}

/** The pane every document's grid starts with, by kind: its component and title. */
const MAIN_PANE: Record<MorphyDocument['kind'], { component: string; title: string }> = {
  home: { component: 'home', title: 'Home' },
  databases: { component: 'databases', title: 'Databases' },
  settings: { component: 'settings', title: 'Settings' },
  logs: { component: 'logs', title: 'Logs' },
  database: { component: 'database', title: 'Search' },
  board: { component: 'board', title: 'Board' },
};

/** The id of a board document's board pane, which is alone in a group without tabs. */
export const BOARD_PANE = 'board';

/** The panes a board document can show beside the board, which can be closed and brought back. */
export const BOARD_SIDE_PANES = {
  notation: 'Notation',
  engine: 'Engine',
  tree: 'Opening tree',
} as const;

export type BoardSidePane = keyof typeof BOARD_SIDE_PANES;

// The analysis panes, which share a group below the notation
const ANALYSIS_PANES: BoardSidePane[] = ['engine', 'tree'];

/**
 * Shows a pane beside a board, or closes it. The engine and the opening tree share a group below
 * the notation; the notation goes above them. With neither shown, a pane goes to the right of
 * the board.
 */
export function toggleBoardPane(api: DockviewApi, id: BoardSidePane) {
  const panel = api.getPanel(id);
  if (panel) {
    api.removePanel(panel);
    return;
  }
  const notation = api.getPanel('notation');
  const analysis = ANALYSIS_PANES.map((other) => api.getPanel(other)).find((p) => p !== undefined);
  const columnHeight = (notation ?? analysis)?.group.api.height ?? api.height;
  let position: AddPanelPositionOptions;
  let initialHeight: number | undefined;
  if (id === 'notation' && analysis) {
    position = { referenceGroup: analysis.group, direction: 'above' };
    initialHeight = Math.round(columnHeight * 0.6);
  } else if (id !== 'notation' && analysis) {
    position = { referenceGroup: analysis.group };
  } else if (id !== 'notation' && notation) {
    position = { referenceGroup: notation.group, direction: 'below' };
    initialHeight = Math.round(columnHeight * 0.4);
  } else {
    position = { referencePanel: BOARD_PANE, direction: 'right' };
  }
  api.addPanel({
    id,
    component: id,
    title: BOARD_SIDE_PANES[id],
    position,
    initialWidth: notation || analysis || api.width <= 0 ? undefined : Math.round(api.width * 0.42),
    initialHeight: initialHeight && initialHeight > 0 ? initialHeight : undefined,
  });
}

/**
 * Sets up a new document's grid: a single pane by kind. A board has the notation to its right,
 * and below that the engine and the opening tree in tabs.
 */
export function defaultLayout(doc: MorphyDocument, api: DockviewApi) {
  const pane = MAIN_PANE[doc.kind];
  const main = api.addPanel({ id: pane.component, component: pane.component, title: pane.title });
  if (doc.kind === 'board') {
    main.group.header.hidden = true;
    toggleBoardPane(api, 'notation');
    toggleBoardPane(api, 'engine');
    toggleBoardPane(api, 'tree');
    api.getPanel('engine')?.api.setActive();
    main.api.setActive();
  }
}

/** Whether a saved grid still has the pane the document's kind always has. */
export function isLayoutComplete(doc: MorphyDocument, api: DockviewApi): boolean {
  return !!api.getPanel(MAIN_PANE[doc.kind].component);
}

/** Whether the value looks like a saved DocumentsState. */
export function isDocumentsState(value: unknown): value is DocumentsState {
  const state = value as DocumentsState | null;
  return (
    !!state &&
    Array.isArray(state.documents) &&
    typeof state.activeId === 'string' &&
    state.documents.every((d) => d && typeof d.id === 'string' && typeof d.kind === 'string') &&
    state.documents.some((d) => d.id === state.activeId)
  );
}

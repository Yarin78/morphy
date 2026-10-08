import type { AddPanelPositionOptions, DockviewApi } from 'dockview-react';
import { SEARCH_KINDS, type SearchKind } from '../search/queries';

// The documents of the app. Each one has its own Dockview grid, which fills the screen when it
// is the active one. Home, All Databases and Logs are singletons; databases, boards and the
// entities of databases (players, events, ...) can be opened any number of times.

/** The kinds of entity a document can be of: a database's players, events and the rest. */
export type EntityKind = Exclude<SearchKind, 'games'>;

export type SingletonKind = 'home' | 'databases' | 'logs';

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
    }
  | {
      kind: 'entity';
      id: string;
      databaseId: string;
      entityKind: EntityKind;
      entityId: number;
      /** Its name or title, as it was when opened: "Kasparov, Garry" */
      title: string;
    };

export type BoardDocument = Extract<MorphyDocument, { kind: 'board' }>;
export type DatabaseDocument = Extract<MorphyDocument, { kind: 'database' }>;
export type EntityDocument = Extract<MorphyDocument, { kind: 'entity' }>;

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
  | { type: 'openEntity'; databaseId: string; entityKind: EntityKind; entityId: number; title: string }
  | { type: 'close'; id: string }
  /** Moves a database, a board or an entity before or after another of its kind, as in the navigator */
  | { type: 'move'; id: string; targetId: string; after: boolean }
  | { type: 'updateBoard'; id: string; changes: Partial<Pick<BoardDocument, 'title' | 'databaseId' | 'gameId'>> };

export function documentTitle(doc: MorphyDocument): string {
  switch (doc.kind) {
    case 'home':
      return 'Home';
    case 'databases':
      return 'All Databases';
    case 'logs':
      return 'Logs';
    case 'database':
      return doc.name;
    case 'board':
      return doc.title ?? `Board ${doc.number}`;
    case 'entity':
      return doc.title;
  }
}

/** The section of the navigator a document is listed in: its kind, and an entity's kind too. */
export function sectionOf(doc: MorphyDocument): string {
  return doc.kind === 'entity' ? `entity:${doc.entityKind}` : doc.kind;
}

/**
 * The open documents in the order the navigator lists them: Home and All Databases, the
 * databases, the boards, the entities by kind (players, events, ...), and Logs at the bottom.
 */
export function navigatorOrder(documents: MorphyDocument[]): MorphyDocument[] {
  const rank: Record<MorphyDocument['kind'], number> = { home: 0, databases: 1, database: 2, board: 3, entity: 4, logs: 5 };
  const rankOf = (d: MorphyDocument) => rank[d.kind] + (d.kind === 'entity' ? SEARCH_KINDS.indexOf(d.entityKind) / 100 : 0);
  // A stable sort, so the documents of a section keep the order they were opened in
  return [...documents].sort((a, b) => rankOf(a) - rankOf(b));
}

/**
 * The document after the active one in the navigator, or before it; round from the ends. Logs is
 * passed over, being for looking into failures, not for working in; from Logs itself, the next is
 * the first document, and the one before the last.
 */
export function adjacentDocument(state: DocumentsState, step: 1 | -1): string {
  const order = navigatorOrder(state.documents).filter((d) => d.kind !== 'logs' || d.id === state.activeId);
  const index = order.findIndex((d) => d.id === state.activeId);
  return order[(index + step + order.length) % order.length].id;
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
  const documents = state.documents.filter((d) => !removed(d));
  if (documents.some((d) => d.id === state.activeId)) return { ...state, documents };
  // The nearest remaining document above the closed one in the navigator, else Home; never Logs,
  // which is for looking into failures, not for working in
  const order = navigatorOrder(state.documents);
  const index = order.findIndex((d) => d.id === state.activeId);
  const before = order
    .slice(0, index)
    .reverse()
    .find((d) => !removed(d) && d.kind !== 'logs');
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
    case 'openEntity': {
      const open = state.documents.find(
        (d) =>
          d.kind === 'entity' &&
          d.databaseId === action.databaseId &&
          d.entityKind === action.entityKind &&
          d.entityId === action.entityId
      );
      if (open) return { ...state, activeId: open.id };
      const doc: MorphyDocument = {
        kind: 'entity',
        id: newId('entity'),
        databaseId: action.databaseId,
        entityKind: action.entityKind,
        entityId: action.entityId,
        title: action.title,
      };
      return { documents: [...state.documents, doc], activeId: doc.id };
    }
    case 'close':
      return without(state, (d) => d.id === action.id);
    case 'move': {
      const moved = state.documents.find((d) => d.id === action.id);
      const target = state.documents.find((d) => d.id === action.targetId);
      if (!moved || !target || moved === target || sectionOf(moved) !== sectionOf(target)) return state;
      const documents = state.documents.filter((d) => d !== moved);
      documents.splice(documents.indexOf(target) + (action.after ? 1 : 0), 0, moved);
      return { ...state, documents };
    }
    case 'updateBoard':
      return {
        ...state,
        documents: state.documents.map((d) => (d.id === action.id && d.kind === 'board' ? { ...d, ...action.changes } : d)),
      };
  }
}

/** The ids of an entity document's panes: its details, and its games below them. */
export const ENTITY_DETAILS_PANE = 'entity-details';
export const ENTITY_GAMES_PANE = 'entity-games';

/** The pane every document's grid starts with, by kind: its component and title. */
const MAIN_PANE: Record<MorphyDocument['kind'], { component: string; title: string }> = {
  home: { component: 'home', title: 'Home' },
  databases: { component: 'databases', title: 'Databases' },
  logs: { component: 'logs', title: 'Logs' },
  database: { component: 'search', title: 'Search' },
  board: { component: 'board', title: 'Board' },
  entity: { component: ENTITY_DETAILS_PANE, title: 'Details' },
};

/** The id of a board document's board pane, which is alone in a group without tabs. */
export const BOARD_PANE = 'board';

/** The id of a database document's search pane, which is alone in a group without tabs. */
export const SEARCH_PANE = 'search';

/** The panes a board document can show beside the board, which can be closed and brought back. */
export const BOARD_SIDE_PANES = {
  notation: 'Notation',
  engine: 'Engine',
  games: 'Games',
} as const;

export type BoardSidePane = keyof typeof BOARD_SIDE_PANES;

// The analysis panes, which share a group below the notation
const ANALYSIS_PANES: BoardSidePane[] = ['engine'];

/**
 * Shows a pane beside a board, or closes it. The games of the position go below the board. The
 * engine goes below the notation, and the notation above the engine; with neither shown, a pane
 * goes to the right of the board.
 */
export function toggleBoardPane(api: DockviewApi, id: BoardSidePane) {
  const panel = api.getPanel(id);
  if (panel) {
    api.removePanel(panel);
    return;
  }
  if (id === 'games') {
    const boardHeight = api.getPanel(BOARD_PANE)?.group.api.height ?? api.height;
    api.addPanel({
      id,
      component: id,
      title: BOARD_SIDE_PANES[id],
      position: { referencePanel: BOARD_PANE, direction: 'below' },
      initialHeight: boardHeight > 0 ? Math.round(boardHeight * 0.35) : undefined,
    });
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

/** The panes a database document can show beside its search, which can be closed and brought back. */
export const DATABASE_SIDE_PANES = {
  preview: 'Preview',
} as const;

export type DatabaseSidePane = keyof typeof DATABASE_SIDE_PANES;

/** Shows a pane beside a database's search, or closes it: the preview, to the right of it. */
export function toggleDatabasePane(api: DockviewApi, id: DatabaseSidePane) {
  const panel = api.getPanel(id);
  if (panel) {
    api.removePanel(panel);
    return;
  }
  api.addPanel({
    id,
    component: id,
    title: DATABASE_SIDE_PANES[id],
    position: { referencePanel: SEARCH_PANE, direction: 'right' },
    // The board fills the preview's width, at most half the height across
    initialWidth: api.width > 0 ? Math.round(Math.min(api.height * 0.5, api.width * 0.45)) : undefined,
  });
}

/**
 * Sets up a new document's grid: a single pane by kind. A board has the notation to its right;
 * the engine and the games of the position are a menu away. A database has the preview of the game
 * picked to the right of its search. An entity has its details above its games, and the preview
 * of the game picked to the right of both.
 */
export function defaultLayout(doc: MorphyDocument, api: DockviewApi) {
  const pane = MAIN_PANE[doc.kind];
  const main = api.addPanel({ id: pane.component, component: pane.component, title: pane.title });
  if (doc.kind === 'board') {
    main.group.header.hidden = true;
    toggleBoardPane(api, 'notation');
    main.api.setActive();
  }
  if (doc.kind === 'database') {
    main.group.header.hidden = true;
    toggleDatabasePane(api, 'preview');
    main.api.setActive();
  }
  if (doc.kind === 'entity') {
    api.addPanel({
      id: 'preview',
      component: 'preview',
      title: DATABASE_SIDE_PANES.preview,
      position: { referencePanel: ENTITY_DETAILS_PANE, direction: 'right' },
      initialWidth: api.width > 0 ? Math.round(Math.min(api.height * 0.5, api.width * 0.45)) : undefined,
    });
    api.addPanel({
      id: ENTITY_GAMES_PANE,
      component: ENTITY_GAMES_PANE,
      title: 'Games',
      position: { referencePanel: ENTITY_DETAILS_PANE, direction: 'below' },
      initialHeight: api.height > 0 ? Math.round(api.height * 0.65) : undefined,
    });
    main.api.setActive();
  }
}

/**
 * Whether a saved grid still has the pane the document's kind always has. A database's search is
 * alone in its group, without tabs; a grid saved before it was isn't used.
 */
export function isLayoutComplete(doc: MorphyDocument, api: DockviewApi): boolean {
  const main = api.getPanel(MAIN_PANE[doc.kind].component);
  if (!main) return false;
  if (doc.kind === 'entity') return !!api.getPanel(ENTITY_GAMES_PANE) && !!api.getPanel('preview');
  return doc.kind !== 'database' || (main.group.header.hidden && main.group.panels.length === 1);
}

/**
 * The documents saved of kinds there still are, as the Settings document is now a dialog. The
 * active one is the first left if it's gone.
 */
export function knownDocuments(state: DocumentsState): DocumentsState {
  const documents = state.documents.filter((d) => d.kind in MAIN_PANE);
  if (documents.length === 0) return INITIAL_STATE;
  const activeId = documents.some((d) => d.id === state.activeId) ? state.activeId : documents[0].id;
  return { documents, activeId };
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

import { useSyncExternalStore } from 'react';
import type { LastMoveStyle, MoveNotation, NotationBarGroup } from 'game-view';
import { NOTATION_BAR_GROUPS } from 'game-view';

// The settings of the app, the same for every document: how boards and the notation look and
// work. They're kept in localStorage, and apply as soon as they're changed. The engine's are kept
// by the analysis store, as the Engine pane changes them too.

export type PieceSet = 'merida' | 'cburnett';
export type BoardTheme = 'brown' | 'blue' | 'green' | 'grey' | 'purple';

export interface BoardSettings {
  /** The files and ranks along the board */
  coordinates: boolean;
  /** The pieces slide when moving through a game */
  animation: boolean;
  lastMove: LastMoveStyle;
  /** The buttons to move through the game, below the board */
  navigation: boolean;
  /** Moves made by pressing the square they go to, the piece moved guessed by the engine */
  destinationMoves: boolean;
  pieceSet: PieceSet;
  boardTheme: BoardTheme;
}

export interface NotationSettings {
  /** How the pieces of moves are written */
  moveNotation: MoveNotation;
  /** The groups of buttons of the bar below the notation, when editing */
  barGroups: NotationBarGroup[];
  /** The size of the notation's text, in pixels */
  fontSize: number;
  /** Whether !, ?, the comment keys and so on annotate the current move */
  annotationKeys: boolean;
}

export interface SearchSettings {
  /** Whether a database's preview shows the variations of the game, not just the main line */
  previewVariations: boolean;
  /** Whether a database's preview shows the annotations: text, symbols, squares and the rest */
  previewCommentary: boolean;
  /** Whether the players of the games found are shown by their full names, not the first initial */
  fullPlayerNames: boolean;
  /** Whether the search shows how long it took and the query it sent */
  debugInfo: boolean;
}

export interface Settings {
  board: BoardSettings;
  notation: NotationSettings;
  search: SearchSettings;
}

export const PIECE_SETS: readonly { id: PieceSet; label: string }[] = [
  { id: 'merida', label: 'Merida' },
  { id: 'cburnett', label: 'Cburnett' },
];

/** The board themes, by the colors of their light and dark squares. */
export const BOARD_THEMES: readonly { id: BoardTheme; label: string; light: string; dark: string }[] = [
  { id: 'brown', label: 'Brown', light: '#f0d9b5', dark: '#b58863' },
  { id: 'blue', label: 'Blue', light: '#dee3e6', dark: '#8ca2ad' },
  { id: 'green', label: 'Green', light: '#ffffdd', dark: '#86a666' },
  { id: 'grey', label: 'Grey', light: '#dcdcdc', dark: '#a5a5a5' },
  { id: 'purple', label: 'Purple', light: '#e6dff0', dark: '#9e90b0' },
];

export const NOTATION_FONT_SIZES = [12, 13, 14, 15, 16, 18, 20, 22];

export const DEFAULT_SETTINGS: Settings = {
  board: {
    coordinates: true,
    animation: true,
    lastMove: 'arrow',
    navigation: true,
    destinationMoves: true,
    pieceSet: 'merida',
    boardTheme: 'brown',
  },
  notation: {
    moveNotation: 'en',
    barGroups: NOTATION_BAR_GROUPS.map((g) => g.id),
    fontSize: 15,
    annotationKeys: true,
  },
  search: {
    previewVariations: false,
    previewCommentary: false,
    fullPlayerNames: false,
    debugInfo: false,
  },
};

const STORAGE_KEY = 'morphy-settings';

function load(): Settings {
  try {
    const saved = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? 'null') as Partial<Settings> | null;
    // Settings added since they were saved get their defaults
    return {
      board: { ...DEFAULT_SETTINGS.board, ...saved?.board },
      notation: { ...DEFAULT_SETTINGS.notation, ...saved?.notation },
      search: { ...DEFAULT_SETTINGS.search, ...saved?.search },
    };
  } catch {
    return DEFAULT_SETTINGS;
  }
}

let settings = load();
const listeners = new Set<() => void>();

function subscribe(listener: () => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function getSettings(): Settings {
  return settings;
}

export function useSettings(): Settings {
  return useSyncExternalStore(subscribe, getSettings);
}

export function setBoardSettings(changes: Partial<BoardSettings>) {
  update({ ...settings, board: { ...settings.board, ...changes } });
}

export function setNotationSettings(changes: Partial<NotationSettings>) {
  update({ ...settings, notation: { ...settings.notation, ...changes } });
}

export function setSearchSettings(changes: Partial<SearchSettings>) {
  update({ ...settings, search: { ...settings.search, ...changes } });
}

/** Puts back the defaults of a section of the settings. */
export function resetSettings(section: keyof Settings) {
  update({ ...settings, [section]: DEFAULT_SETTINGS[section] });
}

function update(next: Settings) {
  settings = next;
  applyToPage(settings);
  listeners.forEach((l) => l());
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(settings));
  } catch {
    // ignore, keeping the settings is a convenience
  }
}

// ── Settings applied by CSS ────────────────────────────────────────────────

/** The settings that are styles, set on the page: the board's look and the notation's size. */
function applyToPage({ board, notation }: Settings) {
  const root = document.documentElement;
  root.dataset.pieces = board.pieceSet;
  root.dataset.boardTheme = board.boardTheme;
  root.style.setProperty('--notation-font-size', `${notation.fontSize}px`);
}

/** A board of 8 by 8 squares, a8 light, as an SVG data URL. */
function boardImage(light: string, dark: string): string {
  let squares = '';
  for (let y = 0; y < 8; y++) {
    for (let x = 0; x < 8; x++) {
      if ((x + y) % 2 === 1) squares += `<rect x="${x}" y="${y}" width="1" height="1"/>`;
    }
  }
  const svg =
    `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 8 8" shape-rendering="crispEdges">` +
    `<rect width="8" height="8" fill="${light}"/><g fill="${dark}">${squares}</g></svg>`;
  return `url("data:image/svg+xml,${encodeURIComponent(svg)}")`;
}

/**
 * The styles of the board themes and of the piece sets besides Merida, react-chessground's own.
 * The Cburnett pieces come with chessground, for every board; they're scoped here to the page
 * having picked them.
 */
async function installStyles() {
  const themes = BOARD_THEMES.map(
    (t) => `:root[data-board-theme='${t.id}'] .cg-wrap { background-image: ${boardImage(t.light, t.dark)}; }`
  ).join('\n');
  const { default: cburnett } = await import('chessground/assets/chessground.cburnett.css?raw');
  const style = document.createElement('style');
  style.dataset.morphy = 'settings';
  style.textContent = `${themes}\n${cburnett.replaceAll('.cg-wrap piece', ":root[data-pieces='cburnett'] .cg-wrap piece")}`;
  document.head.append(style);
}

applyToPage(settings);
void installStyles();

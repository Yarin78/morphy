import { createContext, useContext } from 'react';
import type { GameViewState } from 'game-view';
import type { GameDto } from '../api/types';
import type { DatabaseSearch, ResultsSearch } from '../search/useDatabaseSearch';

/** The game picked in a database's search results, shown in its preview. */
export type PreviewGame =
  | { kind: 'none' }
  | { kind: 'loading'; gameId: number }
  | { kind: 'loaded'; game: GameDto }
  | { kind: 'error'; gameId: number; message: string };

/** A database document's state, shared by its panes: the search, and the game previewed. */
export interface DatabaseView {
  databaseId: string;
  search: DatabaseSearch;
  preview: PreviewGame;
  /** The previewed game, played through on the preview's board */
  view: GameViewState;
  /** Opens a game of the database on a board of its own */
  openGame: (gameId: number) => void;
  /** Handles the keys that move through the previewed game; whether it did */
  previewKeys: (e: React.KeyboardEvent) => boolean;
}

/** What a list of search results is of, and what it does with them. */
export type ResultsSource = Pick<DatabaseView, 'databaseId' | 'openGame' | 'previewKeys'> & { search: ResultsSearch };

export const DatabaseViewContext = createContext<DatabaseView | null>(null);

export function useDatabaseView(): DatabaseView {
  const view = useContext(DatabaseViewContext);
  if (!view) throw new Error('useDatabaseView outside a database document');
  return view;
}

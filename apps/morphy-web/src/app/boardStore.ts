import { createContext, useContext } from 'react';
import type { GameViewState } from 'game-view';

/** The game of a board document, shared by its panes: the board and the notation. */
export const BoardViewContext = createContext<GameViewState | null>(null);

export function useBoardView(): GameViewState {
  const view = useContext(BoardViewContext);
  if (!view) throw new Error('useBoardView outside a board document');
  return view;
}

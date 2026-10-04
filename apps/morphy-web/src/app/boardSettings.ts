import { createContext, useContext } from 'react';

/** How boards look, the same for every board document. */
export interface BoardSettings {
  /** The files and ranks along the board */
  coordinates: boolean;
  /** The pieces slide when moving through a game */
  animation: boolean;
}

export const DEFAULT_BOARD_SETTINGS: BoardSettings = { coordinates: true, animation: true };

export const BoardSettingsContext = createContext<[BoardSettings, (settings: BoardSettings) => void]>([
  DEFAULT_BOARD_SETTINGS,
  () => {},
]);

export function useBoardSettings(): [BoardSettings, (settings: BoardSettings) => void] {
  return useContext(BoardSettingsContext);
}

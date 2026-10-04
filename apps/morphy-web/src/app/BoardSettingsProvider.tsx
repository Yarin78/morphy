import { type ReactNode, useEffect, useState } from 'react';
import { type BoardSettings, BoardSettingsContext, DEFAULT_BOARD_SETTINGS } from './boardSettings';

const STORAGE_KEY = 'morphy-board-settings';

function loadSettings(): BoardSettings {
  try {
    const saved = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? 'null') as Partial<BoardSettings> | null;
    return { ...DEFAULT_BOARD_SETTINGS, ...saved };
  } catch {
    return DEFAULT_BOARD_SETTINGS;
  }
}

/** The board settings, kept in localStorage. */
export function BoardSettingsProvider({ children }: { children: ReactNode }) {
  const [settings, setSettings] = useState(loadSettings);
  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(settings));
    } catch {
      // ignore, keeping the settings is a convenience
    }
  }, [settings]);
  return <BoardSettingsContext.Provider value={[settings, setSettings]}>{children}</BoardSettingsContext.Provider>;
}

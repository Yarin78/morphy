import { useSyncExternalStore } from 'react';

// Whether the settings dialog is open, and on which tab. It's opened from the navigator, and from
// a board's toolbar on the board's tab.

export type SettingsTab = 'general' | 'board' | 'notation' | 'search' | 'engine';

let tab: SettingsTab | null = null;
const listeners = new Set<() => void>();

function set(next: SettingsTab | null) {
  tab = next;
  listeners.forEach((l) => l());
}

export function openSettings(on: SettingsTab = 'general') {
  set(on);
}

export function closeSettings() {
  set(null);
}

/** The tab the settings dialog shows, or null when it's closed. */
export function useSettingsTab(): SettingsTab | null {
  return useSyncExternalStore(
    (listener) => {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
    () => tab
  );
}

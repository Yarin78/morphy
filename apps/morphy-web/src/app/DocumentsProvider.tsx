import { type ReactNode, useCallback, useEffect, useMemo, useReducer, useRef, useState } from 'react';
import type { SerializedDockview } from 'dockview-react';
import { documentsReducer, type DocumentsState, INITIAL_STATE, isDocumentsState, knownDocuments } from './documents';
import { DocumentsContext, type DocumentsStore } from './documentsStore';
import { deleteDraftsExcept } from './drafts';

// The open documents, the active one and each one's grid layout are kept in localStorage, so
// a reload brings them back
const STORAGE_KEY = 'morphy-app';
const VERSION = 1;

interface Saved {
  version: number;
  state: DocumentsState;
  layouts: Record<string, SerializedDockview>;
}

function loadSaved(): Saved | null {
  try {
    const saved = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? 'null') as Saved | null;
    return saved?.version === VERSION && isDocumentsState(saved.state)
      ? { ...saved, state: knownDocuments(saved.state) }
      : null;
  } catch {
    return null;
  }
}

export function DocumentsProvider({ children }: { children: ReactNode }) {
  const [saved] = useState(() => {
    const loaded = loadSaved();
    // The drafts of boards no longer open, as when the documents couldn't be restored
    deleteDraftsExcept((loaded?.state ?? INITIAL_STATE).documents.map((d) => d.id));
    return loaded;
  });
  const [state, dispatch] = useReducer(documentsReducer, saved?.state ?? INITIAL_STATE);
  const layouts = useRef(new Map(Object.entries(saved?.layouts ?? {})));
  const saveTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const stateRef = useRef(state);

  const saveNow = useCallback(() => {
    if (saveTimer.current) clearTimeout(saveTimer.current);
    saveTimer.current = null;
    const current = stateRef.current;
    // Only the layouts of open documents
    const open = new Set(current.documents.map((d) => d.id));
    for (const id of layouts.current.keys()) if (!open.has(id)) layouts.current.delete(id);
    const value: Saved = { version: VERSION, state: current, layouts: Object.fromEntries(layouts.current) };
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(value));
    } catch {
      // ignore, keeping the documents is a convenience
    }
  }, []);

  // Saved shortly after a change, as layouts change on every frame of a drag
  const scheduleSave = useCallback(() => {
    if (saveTimer.current) clearTimeout(saveTimer.current);
    saveTimer.current = setTimeout(saveNow, 300);
  }, [saveNow]);

  // A pending save isn't lost when the page is reloaded or closed
  useEffect(() => {
    const flush = () => saveTimer.current && saveNow();
    window.addEventListener('pagehide', flush);
    return () => window.removeEventListener('pagehide', flush);
  }, [saveNow]);

  useEffect(() => {
    stateRef.current = state;
    scheduleSave();
  }, [state, scheduleSave]);

  const store = useMemo<DocumentsStore>(
    () => ({
      state,
      dispatch,
      layoutOf: (id) => layouts.current.get(id),
      setLayout: (id, layout) => {
        layouts.current.set(id, layout);
        scheduleSave();
      },
    }),
    [state, scheduleSave]
  );

  return <DocumentsContext.Provider value={store}>{children}</DocumentsContext.Provider>;
}

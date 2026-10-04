import { useEffect, useState } from 'react';
import 'dockview-react/dist/styles/dockview.css';
import { DocumentGrid } from './DocumentGrid';
import { BoardSettingsProvider } from './BoardSettingsProvider';
import { DocumentsProvider } from './DocumentsProvider';
import { ChoiceDialog } from './ChoiceDialog';
import { ErrorDialog } from './ErrorDialog';
import { Toasts } from './Toasts';
import { UnsavedChangesDialog } from './UnsavedChangesDialog';
import { getUnsaved } from './unsavedStore';
import { useDocuments } from './documentsStore';
import { Navigator, type NavigatorState } from './Navigator';
import './app.css';

const NAVIGATOR_KEY = 'morphy-navigator';

function loadNavigator(): NavigatorState {
  try {
    const saved = JSON.parse(localStorage.getItem(NAVIGATOR_KEY) ?? 'null') as NavigatorState | null;
    if (saved && typeof saved.collapsed === 'boolean' && typeof saved.width === 'number') return saved;
  } catch {
    // ignore, use the default
  }
  return { collapsed: false, width: 230 };
}

function Workspace() {
  const { state } = useDocuments();
  const [navigator, setNavigator] = useState(loadNavigator);

  // A document's grid is created when it's first shown, and then kept while it's open
  const [mounted, setMounted] = useState(() => new Set([state.activeId]));
  if (!mounted.has(state.activeId)) setMounted(new Set(mounted).add(state.activeId));

  useEffect(() => {
    try {
      localStorage.setItem(NAVIGATOR_KEY, JSON.stringify(navigator));
    } catch {
      // ignore
    }
  }, [navigator]);

  // Leaving the page loses unsaved changes, so the browser asks first
  useEffect(() => {
    const onBeforeUnload = (e: BeforeUnloadEvent) => {
      if (getUnsaved().size > 0) e.preventDefault();
    };
    window.addEventListener('beforeunload', onBeforeUnload);
    return () => window.removeEventListener('beforeunload', onBeforeUnload);
  }, []);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.altKey && !e.ctrlKey && !e.metaKey && e.code === 'KeyB') {
        e.preventDefault();
        setNavigator((n) => ({ ...n, collapsed: !n.collapsed }));
      }
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, []);

  return (
    <div className="morphy-app">
      <Navigator state={navigator} onChange={setNavigator} />
      <main className="documents">
        {state.documents
          .filter((d) => mounted.has(d.id))
          .map((d) => (
            <DocumentGrid key={d.id} doc={d} active={d.id === state.activeId} />
          ))}
      </main>
    </div>
  );
}

export default function App() {
  return (
    <DocumentsProvider>
      <BoardSettingsProvider>
        <Workspace />
        <Toasts />
        <UnsavedChangesDialog />
        <ChoiceDialog />
        <ErrorDialog />
      </BoardSettingsProvider>
    </DocumentsProvider>
  );
}

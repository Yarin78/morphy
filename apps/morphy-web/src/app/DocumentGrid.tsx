import {
  DockviewDefaultTab,
  DockviewReact,
  type DockviewReadyEvent,
  type IDockviewPanelHeaderProps,
  type IDockviewPanelProps,
  themeLight,
} from 'dockview-react';
import type { FunctionComponent } from 'react';
import { BoardDocument } from './BoardDocument';
import { BOARD_PANE, defaultLayout, isLayoutComplete, type MorphyDocument } from './documents';
import { DocumentContext, useDocuments } from './documentsStore';
import { BoardPane } from './panes/BoardPane';
import { DatabasePane } from './panes/DatabasePane';
import { DatabasesPane } from './panes/DatabasesPane';
import { HomePane } from './panes/HomePane';
import { NotationPane } from './panes/NotationPane';
import { SettingsPane } from './panes/SettingsPane';

// The panes a grid can hold, by Dockview component name. Each reads its document from
// DocumentContext; Dockview renders panes through portals, so the context reaches them.
const PANES: Record<string, FunctionComponent<IDockviewPanelProps>> = {
  home: HomePane,
  databases: DatabasesPane,
  settings: SettingsPane,
  database: DatabasePane,
  board: BoardPane,
  notation: NotationPane,
};

// Panes can't be closed yet: nothing could bring them back
function PaneTab(props: IDockviewPanelHeaderProps) {
  return <DockviewDefaultTab {...props} hideClose />;
}

/**
 * One document's Dockview grid. It stays mounted while the document is open, hidden when
 * another document is active, so its panes keep their state.
 */
export function DocumentGrid({ doc, active }: { doc: MorphyDocument; active: boolean }) {
  const { layoutOf, setLayout } = useDocuments();

  const onReady = ({ api }: DockviewReadyEvent) => {
    const layout = layoutOf(doc.id);
    try {
      if (layout) api.fromJSON(layout);
      if (!layout || !isLayoutComplete(doc, api)) {
        api.clear();
        defaultLayout(doc, api);
      }
    } catch {
      api.clear();
      defaultLayout(doc, api);
    }
    // The board's group has no tabs, so nothing can be dropped into it, only beside it
    api.onWillShowOverlay((e) => {
      if (e.position === 'center' && e.group?.panels.some((p) => p.id === BOARD_PANE)) e.preventDefault();
    });
    api.onDidLayoutChange(() => setLayout(doc.id, api.toJSON()));
  };

  const grid = <DockviewReact theme={themeLight} components={PANES} defaultTabComponent={PaneTab} onReady={onReady} />;

  return (
    <div className="document-grid" style={{ display: active ? undefined : 'none' }}>
      <DocumentContext.Provider value={doc}>
        {doc.kind === 'board' ? (
          <BoardDocument doc={doc} active={active}>
            {grid}
          </BoardDocument>
        ) : (
          grid
        )}
      </DocumentContext.Provider>
    </div>
  );
}

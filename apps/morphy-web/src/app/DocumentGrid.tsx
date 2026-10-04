import {
  DockviewDefaultTab,
  type DockviewApi,
  DockviewReact,
  type DockviewReadyEvent,
  type IDockviewPanelHeaderProps,
  type IDockviewPanelProps,
  themeLight,
} from 'dockview-react';
import { type FunctionComponent, useState } from 'react';
import { BoardDocument } from './BoardDocument';
import { BOARD_PANE, defaultLayout, isLayoutComplete, type MorphyDocument } from './documents';
import { DocumentActiveContext, DocumentContext, useDocuments } from './documentsStore';
import { BoardPane } from './panes/BoardPane';
import { DatabasePane } from './panes/DatabasePane';
import { DatabasesPane } from './panes/DatabasesPane';
import { HomePane } from './panes/HomePane';
import { LogsPane } from './panes/LogsPane';
import { NotationPane } from './panes/NotationPane';
import { EnginePane } from './panes/EnginePane';
import { TreePane } from './panes/PlaceholderPanes';

// The panes a grid can hold, by Dockview component name. Each reads its document from
// DocumentContext; Dockview renders panes through portals, so the context reaches them.
const PANES: Record<string, FunctionComponent<IDockviewPanelProps>> = {
  home: HomePane,
  databases: DatabasesPane,
  logs: LogsPane,
  database: DatabasePane,
  board: BoardPane,
  notation: NotationPane,
  engine: EnginePane,
  tree: TreePane,
};

// A document's main pane can't be closed, as nothing would bring it back; the panes beside a
// board can, as its Panes menu brings them back
function PaneTab(props: IDockviewPanelHeaderProps) {
  return <DockviewDefaultTab {...props} hideClose />;
}

/**
 * One document's Dockview grid. It stays mounted while the document is open, hidden when
 * another document is active, so its panes keep their state.
 */
export function DocumentGrid({ doc, active }: { doc: MorphyDocument; active: boolean }) {
  const { layoutOf, setLayout } = useDocuments();
  const [api, setApi] = useState<DockviewApi | null>(null);

  const onReady = ({ api }: DockviewReadyEvent) => {
    setApi(api);
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

  const grid = (
    <DockviewReact
      theme={themeLight}
      components={PANES}
      defaultTabComponent={doc.kind === 'board' ? undefined : PaneTab}
      onReady={onReady}
    />
  );

  return (
    <div className="document-grid" style={{ display: active ? undefined : 'none' }}>
      <DocumentActiveContext.Provider value={active}>
        <DocumentContext.Provider value={doc}>
          {doc.kind === 'board' ? (
            <BoardDocument doc={doc} api={api} active={active}>
              {grid}
            </BoardDocument>
          ) : (
            grid
          )}
        </DocumentContext.Provider>
      </DocumentActiveContext.Provider>
    </div>
  );
}

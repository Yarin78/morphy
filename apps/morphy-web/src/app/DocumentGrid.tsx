import {
  DockviewDefaultTab,
  DockviewReact,
  type DockviewReadyEvent,
  type IDockviewPanelHeaderProps,
  type IDockviewPanelProps,
  themeLight,
} from 'dockview-react';
import type { FunctionComponent } from 'react';
import { defaultLayout, type MorphyDocument } from './documents';
import { DocumentContext, useDocuments } from './documentsStore';
import { BoardPane } from './panes/BoardPane';
import { DatabasePane } from './panes/DatabasePane';
import { DatabasesPane } from './panes/DatabasesPane';
import { HomePane } from './panes/HomePane';
import { SettingsPane } from './panes/SettingsPane';

// The panes a grid can hold, by Dockview component name. Each reads its document from
// DocumentContext; Dockview renders panes through portals, so the context reaches them.
const PANES: Record<string, FunctionComponent<IDockviewPanelProps>> = {
  home: HomePane,
  databases: DatabasesPane,
  settings: SettingsPane,
  database: DatabasePane,
  board: BoardPane,
};

// Panes can't be closed yet: a document has only its one pane, which nothing could bring back
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
      else defaultLayout(doc, api);
    } catch {
      api.clear();
      defaultLayout(doc, api);
    }
    api.onDidLayoutChange(() => setLayout(doc.id, api.toJSON()));
  };

  return (
    <div className="document-grid" style={{ display: active ? undefined : 'none' }}>
      <DocumentContext.Provider value={doc}>
        <DockviewReact theme={themeLight} components={PANES} defaultTabComponent={PaneTab} onReady={onReady} />
      </DocumentContext.Provider>
    </div>
  );
}

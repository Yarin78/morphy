import {
  DockviewDefaultTab,
  type DockviewApi,
  DockviewReact,
  type DockviewReadyEvent,
  type IDockviewPanelHeaderProps,
  type IDockviewPanelProps,
  themeLight,
} from 'dockview-react';
import { type FunctionComponent, useContext, useState } from 'react';
import { BoardDocument } from './BoardDocument';
import { DatabaseDocument } from './DatabaseDocument';
import {
  BOARD_PANE,
  DATABASE_SIDE_PANES,
  defaultLayout,
  ENTITY_DETAILS_PANE,
  ENTITY_GAMES_PANE,
  isLayoutComplete,
  type MorphyDocument,
  SEARCH_PANE,
} from './documents';
import { DocumentActiveContext, DocumentContext, useDocuments } from './documentsStore';
import { EntityDocument } from './EntityDocument';
import { EntityDetailsPane, EntityGamesPane } from './panes/EntityPanes';
import { BoardPane } from './panes/BoardPane';
import { DatabasesPane } from './panes/DatabasesPane';
import { HomePane } from './panes/HomePane';
import { LogsPane } from './panes/LogsPane';
import { NotationPane } from './panes/NotationPane';
import { EnginePane } from './panes/EnginePane';
import { GamesPane } from './panes/GamesPane';
import { PreviewActions, PreviewPane } from './panes/PreviewPane';
import { SearchPane } from './panes/SearchPane';

// The panes a grid can hold, by Dockview component name. Each reads its document from
// DocumentContext; Dockview renders panes through portals, so the context reaches them.
const PANES: Record<string, FunctionComponent<IDockviewPanelProps>> = {
  home: HomePane,
  databases: DatabasesPane,
  logs: LogsPane,
  search: SearchPane,
  preview: PreviewPane,
  board: BoardPane,
  notation: NotationPane,
  engine: EnginePane,
  games: GamesPane,
  [ENTITY_DETAILS_PANE]: EntityDetailsPane,
  [ENTITY_GAMES_PANE]: EntityGamesPane,
};

// A document's main pane can't be closed, as nothing would bring it back; the panes beside a
// board or a database can, as their View menus bring them back. An entity's panes are all there
// is to it, so none of them can.
function PaneTab(props: IDockviewPanelHeaderProps) {
  const doc = useContext(DocumentContext);
  const closable = doc?.kind === 'database' && props.api.id in DATABASE_SIDE_PANES;
  return <DockviewDefaultTab {...props} hideClose={!closable} />;
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
    // The board's and the search's groups have no tabs, so nothing can be dropped into them, only
    // beside them
    api.onWillShowOverlay((e) => {
      if (e.position === 'center' && e.group?.panels.some((p) => p.id === BOARD_PANE || p.id === SEARCH_PANE)) {
        e.preventDefault();
      }
    });
    api.onDidLayoutChange(() => setLayout(doc.id, api.toJSON()));
  };

  const grid = (
    <DockviewReact
      theme={themeLight}
      components={PANES}
      defaultTabComponent={doc.kind === 'board' ? undefined : PaneTab}
      rightHeaderActionsComponent={doc.kind === 'database' || doc.kind === 'entity' ? PreviewActions : undefined}
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
          ) : doc.kind === 'database' ? (
            <DatabaseDocument doc={doc} api={api} active={active}>
              {grid}
            </DatabaseDocument>
          ) : doc.kind === 'entity' ? (
            <EntityDocument doc={doc} api={api} active={active}>
              {grid}
            </EntityDocument>
          ) : (
            grid
          )}
        </DocumentContext.Provider>
      </DocumentActiveContext.Provider>
    </div>
  );
}

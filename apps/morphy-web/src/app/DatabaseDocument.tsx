import { type ReactNode, useEffect, useState } from 'react';
import type { DockviewApi } from 'dockview-react';
import { TbAdjustments } from 'react-icons/tb';
import { fetchDatabases } from '../api/client';
import type { DatabaseResponse } from '../api/types';
import { useDatabaseSearch } from '../search/useDatabaseSearch';
import { closeDocuments } from './closeDocuments';
import { type DatabaseView, DatabaseViewContext } from './databaseStore';
import {
  DATABASE_SIDE_PANES,
  type DatabaseDocument as DatabaseDoc,
  type DatabaseSidePane,
  defaultLayout,
  toggleDatabasePane,
} from './documents';
import { useDocuments } from './documentsStore';
import { type Menu, MenuBar } from './MenuBar';
import { CLOSE_DOCUMENT_SHORTCUT, shortcutLabel } from './shortcuts';
import { openSettings, useSettingsTab } from './settingsDialogStore';
import { useGamePreview } from './useGamePreview';

/**
 * A database document: its games and entities searched in the search pane, the game picked
 * previewed beside it. The search and the preview are kept here, above the document's grid, and
 * shared with its panes.
 */
export function DatabaseDocument({
  doc,
  api,
  active,
  children,
}: {
  doc: DatabaseDoc;
  /** The document's grid, once it's ready */
  api: DockviewApi | null;
  active: boolean;
  children: ReactNode;
}) {
  const { dispatch } = useDocuments();
  const search = useDatabaseSearch(doc.databaseId);

  const [database, setDatabase] = useState<DatabaseResponse | null>(null);
  useEffect(() => {
    fetchDatabases()
      .then((res) => setDatabase(res.databases.find((db) => db.id === doc.databaseId) ?? null))
      .catch(() => {});
  }, [doc.databaseId]);

  const gamePreview = useGamePreview(doc.databaseId, search);
  const databaseView: DatabaseView = {
    databaseId: doc.databaseId,
    search,
    ...gamePreview,
    openGame: (gameId) => dispatch({ type: 'openBoard', databaseId: doc.databaseId, gameId }),
  };

  return (
    <DatabaseViewContext.Provider value={databaseView}>
      <div className="board-document">
        <DatabaseCommands doc={doc} api={api} active={active} database={database} />
        <div className="board-grid">{children}</div>
      </div>
    </DatabaseViewContext.Provider>
  );
}

function DatabaseCommands({
  doc,
  api,
  active,
  database,
}: {
  doc: DatabaseDoc;
  api: DockviewApi | null;
  active: boolean;
  database: DatabaseResponse | null;
}) {
  const { dispatch } = useDocuments();
  const settingsOpen = useSettingsTab() !== null;

  // The view menu shows which panes are open
  const [, setLayoutVersion] = useState(0);
  useEffect(() => {
    if (!api) return;
    const bump = () => setLayoutVersion((v) => v + 1);
    const subs = [api.onDidAddPanel(bump), api.onDidRemovePanel(bump)];
    return () => subs.forEach((s) => s.dispose());
  }, [api]);

  const menus: Menu[] = [
    {
      title: 'Database',
      items: [
        { label: 'New Board', action: () => dispatch({ type: 'openBoard', databaseId: doc.databaseId }) },
        'separator',
        // The app takes the keys, as they work in fields too
        {
          label: 'Close Database',
          hint: shortcutLabel(CLOSE_DOCUMENT_SHORTCUT),
          action: () => void closeDocuments([doc], dispatch),
        },
      ],
    },
    {
      title: 'View',
      items: [
        ...(Object.keys(DATABASE_SIDE_PANES) as DatabaseSidePane[]).map((id) => ({
          label: DATABASE_SIDE_PANES[id],
          checked: !!api?.getPanel(id),
          disabled: !api,
          action: () => api && toggleDatabasePane(api, id),
        })),
        'separator',
        {
          label: 'Reset Layout',
          disabled: !api,
          action: () => {
            if (!api) return;
            api.clear();
            defaultLayout(doc, api);
          },
        },
        'separator',
        { label: 'Board Settings…', icon: <TbAdjustments />, action: () => openSettings('board') },
        { label: 'Search Settings…', action: () => openSettings('search') },
      ],
    },
  ];

  return (
    <div className="board-commands">
      <div className="board-menubar">
        <MenuBar menus={menus} enabled={active && !settingsOpen} />
        <span className="board-status" title={database?.path}>
          {database?.readOnly && <span className="database-readonly">read-only</span>} {database?.displayName ?? doc.name}
        </span>
      </div>
    </div>
  );
}

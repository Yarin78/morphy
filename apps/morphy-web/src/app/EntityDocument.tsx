import { type ReactNode, useEffect, useMemo, useState } from 'react';
import type { DockviewApi } from 'dockview-react';
import { TbAdjustments } from 'react-icons/tb';
import { fetchDatabases, fetchEntity } from '../api/client';
import type { DatabaseResponse } from '../api/types';
import { entityLabel } from '../search/columns';
import { ENTITY_ID_FIELDS, SEARCH_KIND_SINGULAR } from '../search/queries';
import { useDatabaseSearch } from '../search/useDatabaseSearch';
import { closeDocuments } from './closeDocuments';
import { type DatabaseView, DatabaseViewContext } from './databaseStore';
import { defaultLayout, type EntityDocument as EntityDoc } from './documents';
import { useDocuments } from './documentsStore';
import { EntityContext, type EntityState } from './entityStore';
import { type Menu, MenuBar } from './MenuBar';
import { CLOSE_DOCUMENT_SHORTCUT, shortcutLabel } from './shortcuts';
import { openSettings, useSettingsTab } from './settingsDialogStore';
import { useGamePreview } from './useGamePreview';

/**
 * An entity's document: a player, an event or another entity of a database, its details above
 * its games, the game picked previewed beside them. The games are a database's game search,
 * limited to the entity; their preview is as in the database's document.
 */
export function EntityDocument({
  doc,
  api,
  active,
  children,
}: {
  doc: EntityDoc;
  /** The document's grid, once it's ready */
  api: DockviewApi | null;
  active: boolean;
  children: ReactNode;
}) {
  const { dispatch } = useDocuments();
  const constraint = useMemo(
    () => ({ field: ENTITY_ID_FIELDS[doc.entityKind], id: doc.entityId, label: `${SEARCH_KIND_SINGULAR[doc.entityKind]}: ${doc.title}` }),
    [doc.entityKind, doc.entityId, doc.title]
  );
  const search = useDatabaseSearch(doc.databaseId, constraint);
  const gamePreview = useGamePreview(doc.databaseId, search);

  const [entity, setEntity] = useState<EntityState>({ kind: 'loading' });
  useEffect(() => {
    let cancelled = false;
    fetchEntity<Record<string, unknown>>(doc.databaseId, doc.entityKind, doc.entityId)
      .then((e) => !cancelled && setEntity({ kind: 'loaded', entity: e }))
      .catch(
        (err: unknown) =>
          !cancelled && setEntity({ kind: 'error', message: err instanceof Error ? err.message : String(err) })
      );
    return () => {
      cancelled = true;
    };
  }, [doc.databaseId, doc.entityKind, doc.entityId]);

  const databaseView: DatabaseView = {
    databaseId: doc.databaseId,
    search,
    ...gamePreview,
    openGame: (gameId) => dispatch({ type: 'openBoard', databaseId: doc.databaseId, gameId }),
  };

  return (
    <DatabaseViewContext.Provider value={databaseView}>
      <EntityContext.Provider value={entity}>
        <div className="board-document">
          <EntityCommands doc={doc} api={api} active={active} entity={entity} />
          <div className="board-grid">{children}</div>
        </div>
      </EntityContext.Provider>
    </DatabaseViewContext.Provider>
  );
}

function EntityCommands({
  doc,
  api,
  active,
  entity,
}: {
  doc: EntityDoc;
  api: DockviewApi | null;
  active: boolean;
  entity: EntityState;
}) {
  const { state, dispatch } = useDocuments();
  const settingsOpen = useSettingsTab() !== null;
  const singular = SEARCH_KIND_SINGULAR[doc.entityKind];

  const [database, setDatabase] = useState<DatabaseResponse | null>(null);
  useEffect(() => {
    fetchDatabases()
      .then((res) => setDatabase(res.databases.find((db) => db.id === doc.databaseId) ?? null))
      .catch(() => {});
  }, [doc.databaseId]);

  const openDatabase = () => {
    const open = state.documents.find((d) => d.kind === 'database' && d.databaseId === doc.databaseId);
    if (open) dispatch({ type: 'activate', id: open.id });
    else if (database) dispatch({ type: 'openDatabase', databaseId: doc.databaseId, name: database.displayName });
  };

  const menus: Menu[] = [
    {
      title: singular,
      items: [
        { label: 'Open Database', disabled: !database, action: openDatabase },
        'separator',
        // The app takes the keys, as they work in fields too
        {
          label: `Close ${singular}`,
          hint: shortcutLabel(CLOSE_DOCUMENT_SHORTCUT),
          action: () => void closeDocuments([doc], dispatch),
        },
      ],
    },
    {
      title: 'View',
      items: [
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

  const label = entity.kind === 'loaded' ? entityLabel(doc.entityKind, entity.entity) : `${singular}: ${doc.title}`;
  return (
    <div className="board-commands">
      <div className="board-menubar">
        <MenuBar menus={menus} enabled={active && !settingsOpen} />
        <span className="board-status" title={database?.path}>
          {label} · {database?.displayName ?? doc.databaseId}
        </span>
      </div>
    </div>
  );
}

import { type ReactNode, useEffect, useMemo, useState } from 'react';
import type { DockviewApi } from 'dockview-react';
import { useGameView } from 'game-view';
import { TbAdjustments } from 'react-icons/tb';
import type { ChessGame } from 'game-view';
import { fetchDatabases, fetchGame } from '../api/client';
import type { DatabaseResponse } from '../api/types';
import { gameDtoToChessGame } from '../game/gameDtoAdapter';
import { previewGame as previewed } from '../game/previewGame';
import { useDatabaseSearch } from '../search/useDatabaseSearch';
import { closeDocuments } from './closeDocuments';
import { type DatabaseView, DatabaseViewContext, type PreviewGame } from './databaseStore';
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
import { useSettings } from './settings';

// How long a game is picked before it's fetched for the preview, so that holding a key down
// through the results doesn't fetch every game passed
const PREVIEW_DELAY_MS = 120;

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

  // The game picked in the game results, fetched for the preview
  const gameResults = search.kind === 'games' ? search.current : null;
  const pickedRow =
    gameResults?.selected != null ? (gameResults.results?.rows[gameResults.selected] as { id: number; type?: string }) : null;
  const pickedId = pickedRow && pickedRow.type !== 'text' ? pickedRow.id : null;
  const [preview, setPreview] = useState<PreviewGame>({ kind: 'none' });
  useEffect(() => {
    if (pickedId == null) return;
    let cancelled = false;
    // The game shown before stays, as it is, until this one is fetched, so the board isn't taken
    // away and put back, or faded, on every game picked
    const timer = setTimeout(() => {
      fetchGame(doc.databaseId, pickedId)
        .then((game) => !cancelled && setPreview({ kind: 'loaded', game }))
        .catch(
          (err: unknown) =>
            !cancelled &&
            setPreview({ kind: 'error', gameId: pickedId, message: err instanceof Error ? err.message : String(err) })
        );
    }, PREVIEW_DELAY_MS);
    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [doc.databaseId, pickedId]);

  const previewGame = preview.kind === 'loaded' ? preview.game : null;
  const { notation: notationSettings, search: searchSettings } = useSettings();
  // Only the main line's moves, unless the settings show the variations or the commentary
  const { previewVariations: variations, previewCommentary: commentary } = searchSettings;
  const selectedGame = useMemo<ChessGame | null>(
    () => (previewGame ? previewed(gameDtoToChessGame(previewGame), { variations, commentary }) : null),
    [previewGame, variations, commentary]
  );
  // The preview takes no keys of its own: the results and the preview pane pass them on, so the
  // arrows up and down pick a game and left and right move through it
  const view = useGameView({
    selectedGame,
    initialOrientation: 'white',
    readOnly: true,
    keysEnabled: false,
    notation: notationSettings.moveNotation,
  });

  // The game shown until the one picked is fetched is the one picked before: it's loading
  const shown: PreviewGame =
    pickedId == null
      ? { kind: 'none' }
      : (preview.kind === 'loaded' && preview.game.id !== pickedId) ||
          (preview.kind === 'error' && preview.gameId !== pickedId) ||
          preview.kind === 'none'
        ? { kind: 'loading', gameId: pickedId }
        : preview;

  const databaseView: DatabaseView = {
    databaseId: doc.databaseId,
    search,
    preview: shown,
    view,
    openGame: (gameId) => dispatch({ type: 'openBoard', databaseId: doc.databaseId, gameId }),
    previewKeys: (e) => {
      // Not while the game picked is loading, as the keys would move through the one before
      if (shown.kind !== 'loaded' || e.altKey || e.ctrlKey || e.metaKey) return false;
      const step: Record<string, () => void> = {
        ArrowLeft: () => view.canGoBack() && view.goToPreviousMove(),
        ArrowRight: () => view.canGoForward() && view.handleNextMove(),
        Home: () => view.canGoBack() && view.goToStart(),
        End: () => view.canGoForward() && view.goToEnd(),
      };
      const action = step[e.key];
      if (!action) return false;
      e.preventDefault();
      action();
      return true;
    },
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

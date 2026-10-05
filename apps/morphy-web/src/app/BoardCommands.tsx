import { type ReactNode, useEffect, useState } from 'react';
import type { DockviewApi } from 'dockview-react';
import type { GameViewState } from 'game-view';
import {
  TbAdjustments,
  TbArrowBackUp,
  TbArrowForwardUp,
  TbBinaryTree,
  TbChessKnight,
  TbCpu,
  TbDeviceFloppy,
  TbFileExport,
  TbInfoCircle,
  TbNotes,
  TbRestore,
  TbSwitchVertical,
} from 'react-icons/tb';
import { setAnalysisOn } from '../engine/analysis';
import { closeDocuments } from './closeDocuments';
import { BOARD_SIDE_PANES, type BoardSidePane, type BoardDocument, defaultLayout, toggleBoardPane } from './documents';
import { useDocuments } from './documentsStore';
import { type Menu, MenuBar } from './MenuBar';
import { openSettings, useSettingsTab } from './settingsDialogStore';
import { CLOSE_DOCUMENT_SHORTCUT, MAC, shortcutLabel } from './shortcuts';

// The keys of the panes beside the board
const PANE_SHORTCUTS: Record<BoardSidePane, string> = { notation: 'Alt+N', engine: 'Alt+E', tree: 'Alt+T' };
const PANE_ICONS: Record<BoardSidePane, ReactNode> = {
  notation: <TbNotes />,
  engine: <TbCpu />,
  tree: <TbBinaryTree />,
};

export interface BoardCommandsProps {
  doc: BoardDocument;
  /** The document's grid, once it's ready */
  api: DockviewApi | null;
  /** Whether the document is the one shown, which alone takes the shortcuts */
  active: boolean;
  view: GameViewState;
  save: () => void;
  /** Saves the game as a new one, in a database picked first */
  saveAs: () => void;
  /** Whether saving asks for the database first, the game being in none */
  savePicksDatabase: boolean;
  /** The name of the game's database if it's read-only, which the game can't be saved to */
  readOnlyDatabase: string | null;
  canSave: boolean;
  saving: boolean;
  /** Whether the game has changes not yet saved */
  unsaved: boolean;
  /** Throws the unsaved changes away */
  revertToSaved: () => void;
  /** Where the game is from, shown at the right */
  status: string;
  error: string | null;
}

function ToolButton({
  icon,
  label,
  title,
  onClick,
  disabled,
  pressed,
}: {
  icon: ReactNode;
  label: string;
  title: string;
  onClick: () => void;
  disabled?: boolean;
  pressed?: boolean;
}) {
  // The title is on a wrapper, as browsers don't all show it for a disabled button, which is
  // when it may matter most: saying why
  return (
    <span className="toolbar-button-wrap" title={title}>
      <button
        className={`toolbar-button${pressed ? ' pressed' : ''}`}
        aria-pressed={pressed}
        aria-label={title}
        disabled={disabled}
        onClick={onClick}
      >
        {icon}
        <span>{label}</span>
      </button>
    </span>
  );
}

/**
 * The menu bar and toolbar of a board document, above all its panes: saving and closing the
 * game, editing it, moving through it, the panes beside the board, and how the board looks.
 */
export function BoardCommands({
  doc,
  api,
  active,
  view,
  save,
  saveAs,
  savePicksDatabase,
  readOnlyDatabase,
  canSave,
  saving,
  unsaved,
  revertToSaved,
  status,
  error,
}: BoardCommandsProps) {
  const { dispatch } = useDocuments();
  const settingsOpen = useSettingsTab() !== null;

  // The view menu and the buttons show which panes are open
  const [, setLayoutVersion] = useState(0);
  useEffect(() => {
    if (!api) return;
    const bump = () => setLayoutVersion((v) => v + 1);
    const subs = [api.onDidAddPanel(bump), api.onDidRemovePanel(bump)];
    return () => subs.forEach((s) => s.dispose());
  }, [api]);
  const paneShown = (id: BoardSidePane) => !!api?.getPanel(id);
  const togglePane = (id: BoardSidePane) => {
    if (!api) return;
    // The engine pane is shown to see the engine's analysis, so the engine starts with it
    if (id === 'engine' && !paneShown(id)) setAnalysisOn(true);
    toggleBoardPane(api, id);
  };

  const hasGame = !!view.selectedGame;
  const menus: Menu[] = [
    {
      title: 'Game',
      items: [
        {
          // A menu item has no tooltip, so it says why it can't be used
          label: readOnlyDatabase ? 'Save (the database is read-only)' : savePicksDatabase ? 'Save to Database…' : 'Save',
          icon: <TbDeviceFloppy />,
          shortcut: 'Cmd+S',
          disabled: !canSave || !!readOnlyDatabase,
          action: save,
        },
        { label: 'Save As…', shortcut: 'Shift+Cmd+S', disabled: !canSave || !hasGame, action: saveAs },
        { label: 'Export PGN…', icon: <TbFileExport />, disabled: true },
        'separator',
        { label: 'Revert to Saved', icon: <TbRestore />, disabled: !unsaved, action: revertToSaved },
        'separator',
        { label: 'Edit Game Info…', icon: <TbInfoCircle />, disabled: !hasGame, action: view.openGameInfo },
        'separator',
        // The app takes the keys, as they work in fields too
        { label: 'Close Board', hint: shortcutLabel(CLOSE_DOCUMENT_SHORTCUT), action: () => void closeDocuments([doc], dispatch) },
      ],
    },
    {
      title: 'Edit',
      items: [
        // The board takes these keys itself
        {
          label: 'Undo',
          icon: <TbArrowBackUp />,
          hint: shortcutLabel('Cmd+Z'),
          disabled: !view.canUndo(),
          action: view.handleUndo,
        },
        {
          label: 'Redo',
          icon: <TbArrowForwardUp />,
          hint: shortcutLabel(MAC ? 'Shift+Cmd+Z' : 'Cmd+Y'),
          disabled: !view.canRedo(),
          action: view.handleRedo,
        },
        'separator',
        { label: 'Setup Position…', icon: <TbChessKnight />, disabled: true },
      ],
    },
    {
      title: 'Board',
      items: [
        { label: 'First Move', hint: 'Home', disabled: !hasGame || !view.canGoBack(), action: view.goToStart },
        { label: 'Previous Move', hint: '←', disabled: !hasGame || !view.canGoBack(), action: view.goToPreviousMove },
        { label: 'Next Move', hint: '→', disabled: !hasGame || !view.canGoForward(), action: view.handleNextMove },
        { label: 'Last Move', hint: 'End', disabled: !hasGame || !view.canGoForward(), action: view.goToEnd },
        'separator',
        { label: 'Flip Board', icon: <TbSwitchVertical />, shortcut: 'Alt+F', action: view.flipBoard },
      ],
    },
    {
      title: 'View',
      items: [
        ...(Object.keys(BOARD_SIDE_PANES) as BoardSidePane[]).map((id) => ({
          label: BOARD_SIDE_PANES[id],
          shortcut: PANE_SHORTCUTS[id],
          checked: paneShown(id),
          disabled: !api,
          action: () => togglePane(id),
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
        { label: 'Flip Board', icon: <TbSwitchVertical />, hint: shortcutLabel('Alt+F'), action: view.flipBoard },
        { label: 'Board Settings…', icon: <TbAdjustments />, action: () => openSettings('board') },
      ],
    },
  ];

  return (
    <div className="board-commands">
      <div className="board-menubar">
        <MenuBar menus={menus} enabled={active && !settingsOpen} />
        <span className="board-status">
          {unsaved && (
            <span className="board-unsaved" title="The game has changes that aren't saved">
              ● Unsaved changes
            </span>
          )}
          {status}
        </span>
        {error && <span className="board-error">{error}</span>}
      </div>
      <div className="board-toolbar" role="toolbar">
        <ToolButton
          icon={<TbDeviceFloppy />}
          // The label stays, so the buttons don't move; saving shows as the button disabled
          label="Save"
          title={
            readOnlyDatabase
              ? `${readOnlyDatabase} is read-only, so the game can't be saved to it`
              : `${savePicksDatabase ? 'Save the game to a database' : 'Save the game'} (${shortcutLabel('Cmd+S')})`
          }
          disabled={!canSave || saving || !!readOnlyDatabase}
          onClick={save}
        />
        <span className="toolbar-sep" />
        <ToolButton
          icon={<TbSwitchVertical />}
          label="Flip"
          title={`Flip the board (${shortcutLabel('Alt+F')})`}
          onClick={view.flipBoard}
        />
        <ToolButton
          icon={<TbChessKnight />}
          label="Setup"
          title="Set up a position (not yet)"
          disabled
          onClick={() => {}}
        />
        <span className="toolbar-sep" />
        {(Object.keys(BOARD_SIDE_PANES) as BoardSidePane[]).map((id) => (
          <ToolButton
            key={id}
            icon={PANE_ICONS[id]}
            label={BOARD_SIDE_PANES[id]}
            title={`${paneShown(id) ? 'Hide' : 'Show'} the ${BOARD_SIDE_PANES[id].toLowerCase()} (${shortcutLabel(PANE_SHORTCUTS[id])})`}
            pressed={paneShown(id)}
            disabled={!api}
            onClick={() => togglePane(id)}
          />
        ))}
        <span className="toolbar-sep" />
        <ToolButton
          icon={<TbAdjustments />}
          label="Settings"
          title="Board settings"
          onClick={() => openSettings('board')}
        />
      </div>
    </div>
  );
}

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
  TbSwitchVertical,
} from 'react-icons/tb';
import { BoardSettingsDialog } from './BoardSettingsDialog';
import { BOARD_SIDE_PANES, type BoardSidePane, type BoardDocument, defaultLayout, toggleBoardPane } from './documents';
import { useDocuments } from './documentsStore';
import { type Menu, MenuBar } from './MenuBar';
import { MAC, shortcutLabel } from './shortcuts';

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
  /** Whether saving asks for the database first, the game being in none */
  savePicksDatabase: boolean;
  canSave: boolean;
  saving: boolean;
  /** Where the game is from, shown at the right */
  status: string;
  message: string | null;
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
  return (
    <button
      className={`toolbar-button${pressed ? ' pressed' : ''}`}
      title={title}
      aria-pressed={pressed}
      disabled={disabled}
      onClick={onClick}
    >
      {icon}
      <span>{label}</span>
    </button>
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
  savePicksDatabase,
  canSave,
  saving,
  status,
  message,
  error,
}: BoardCommandsProps) {
  const { dispatch } = useDocuments();
  const [settingsOpen, setSettingsOpen] = useState(false);

  // The panes menu and buttons show which panes are open
  const [, setLayoutVersion] = useState(0);
  useEffect(() => {
    if (!api) return;
    const bump = () => setLayoutVersion((v) => v + 1);
    const subs = [api.onDidAddPanel(bump), api.onDidRemovePanel(bump)];
    return () => subs.forEach((s) => s.dispose());
  }, [api]);
  const paneShown = (id: BoardSidePane) => !!api?.getPanel(id);
  const togglePane = (id: BoardSidePane) => api && toggleBoardPane(api, id);

  const hasGame = !!view.selectedGame;
  const menus: Menu[] = [
    {
      title: 'Game',
      items: [
        {
          label: savePicksDatabase ? 'Save to Database…' : 'Save',
          icon: <TbDeviceFloppy />,
          shortcut: 'Cmd+S',
          disabled: !canSave,
          action: save,
        },
        { label: 'Save As…', disabled: true },
        { label: 'Export PGN…', icon: <TbFileExport />, disabled: true },
        'separator',
        { label: 'Edit Game Info…', icon: <TbInfoCircle />, disabled: !hasGame, action: view.openGameInfo },
        'separator',
        { label: 'Close Board', action: () => dispatch({ type: 'close', id: doc.id }) },
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
      title: 'Panes',
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
      ],
    },
    {
      title: 'View',
      items: [
        { label: 'Flip Board', icon: <TbSwitchVertical />, hint: shortcutLabel('Alt+F'), action: view.flipBoard },
        'separator',
        { label: 'Board Settings…', icon: <TbAdjustments />, action: () => setSettingsOpen(true) },
      ],
    },
  ];

  return (
    <div className="board-commands">
      <div className="board-menubar">
        <MenuBar menus={menus} enabled={active && !settingsOpen} />
        <span className="board-status">{status}</span>
        {message && <span className="board-message">{message}</span>}
        {error && <span className="board-error">{error}</span>}
      </div>
      <div className="board-toolbar" role="toolbar">
        <ToolButton
          icon={<TbDeviceFloppy />}
          label={saving ? 'Saving…' : 'Save'}
          title={`${savePicksDatabase ? 'Save the game to a database' : 'Save the game'} (${shortcutLabel('Cmd+S')})`}
          disabled={!canSave}
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
          onClick={() => setSettingsOpen(true)}
        />
      </div>
      {settingsOpen && <BoardSettingsDialog onClose={() => setSettingsOpen(false)} />}
    </div>
  );
}

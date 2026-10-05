import { type DragEvent, type PointerEvent as ReactPointerEvent, useState, useSyncExternalStore } from 'react';
import { getSnapshot as getLogSnapshot, subscribe as subscribeLogs } from '../logs/logStore';
import { closeDocuments } from './closeDocuments';
import { getUnsaved, subscribeUnsaved } from './unsavedStore';
import { documentTitle, type MorphyDocument, type SingletonKind } from './documents';
import { useDocuments } from './documentsStore';
import { openSettings } from './settingsDialogStore';

// The navigator on the left: the singleton documents, New Board, the open databases and
// boards, and Close All and Settings at the bottom. It collapses to icons, and resizes by
// dragging its right edge; dragged narrow enough, it snaps to icons. The databases and the boards
// are dragged up and down within their section to order them.

export const COLLAPSED_WIDTH = 44;
const MIN_EXPANDED = 160;
const MAX_EXPANDED = 420;
const SNAP_BELOW = 110;

export interface NavigatorState {
  collapsed: boolean;
  width: number;
}

const ICONS: Record<MorphyDocument['kind'], string> = {
  home: '⌂',
  databases: '☰',
  logs: '≣',
  database: '▤',
  board: '♞',
};

/** For an item that's dragged to order it: the handlers, and where a drop on it would go */
interface NavDrag {
  onDragStart: (e: DragEvent<HTMLDivElement>) => void;
  onDragOver: (e: DragEvent<HTMLDivElement>) => void;
  onDrop: (e: DragEvent<HTMLDivElement>) => void;
  onDragEnd: () => void;
  dropSide: 'before' | 'after' | null;
  dragging: boolean;
}

function NavItem({
  icon,
  label,
  collapsed,
  active,
  disabled,
  badge,
  unsaved,
  onClick,
  onClose,
  drag,
}: {
  icon: string;
  label: string;
  collapsed: boolean;
  active?: boolean;
  disabled?: boolean;
  /** A count to draw attention to, as of errors not yet seen */
  badge?: number;
  /** Whether it has changes not yet saved, shown as a dot */
  unsaved?: boolean;
  onClick: () => void;
  onClose?: () => void;
  drag?: NavDrag;
}) {
  return (
    <div
      className={
        `nav-item${active ? ' active' : ''}${disabled ? ' disabled' : ''}` +
        `${drag?.dropSide ? ` drop-${drag.dropSide}` : ''}${drag?.dragging ? ' dragging' : ''}`
      }
      title={collapsed ? label : undefined}
      onClick={disabled ? undefined : onClick}
      draggable={!!drag}
      onDragStart={drag?.onDragStart}
      onDragOver={drag?.onDragOver}
      onDrop={drag?.onDrop}
      onDragEnd={drag?.onDragEnd}
    >
      <span className="nav-icon">
        {icon}
        {!!badge && collapsed && <span className="nav-badge nav-badge-dot" />}
        {unsaved && collapsed && <span className="nav-unsaved-dot" />}
      </span>
      {!collapsed && <span className="nav-label">{label}</span>}
      {!!badge && !collapsed && <span className="nav-badge">{badge}</span>}
      {unsaved && !collapsed && (
        <span className="nav-unsaved" title="Unsaved changes">
          ●
        </span>
      )}
      {!collapsed && onClose && (
        <button
          className="nav-close"
          title="Close"
          onClick={(e) => {
            e.stopPropagation();
            onClose();
          }}
        >
          ✕
        </button>
      )}
    </div>
  );
}

export function Navigator({ state, onChange }: { state: NavigatorState; onChange: (state: NavigatorState) => void }) {
  const { state: docs, dispatch } = useDocuments();
  const [dragWidth, setDragWidth] = useState<number | null>(null);

  const collapsed = dragWidth === null ? state.collapsed : dragWidth < SNAP_BELOW;
  const width = collapsed ? COLLAPSED_WIDTH : (dragWidth ?? state.width);

  const databases = docs.documents.filter((d) => d.kind === 'database');
  const boards = docs.documents.filter((d) => d.kind === 'board');

  // The item dragged to order it, and where it would go if dropped now
  const [dragged, setDragged] = useState<MorphyDocument | null>(null);
  const [drop, setDrop] = useState<{ id: string; after: boolean } | null>(null);
  const endDrag = () => {
    setDragged(null);
    setDrop(null);
  };
  const dragOf = (d: MorphyDocument): NavDrag => ({
    dragging: dragged?.id === d.id,
    dropSide: drop?.id === d.id ? (drop.after ? 'after' : 'before') : null,
    onDragStart: (e: DragEvent<HTMLDivElement>) => {
      e.dataTransfer.effectAllowed = 'move';
      e.dataTransfer.setData('text/plain', documentTitle(d));
      setDragged(d);
    },
    // Only within its section: a database among the databases, a board among the boards
    onDragOver: (e: DragEvent<HTMLDivElement>) => {
      if (!dragged || dragged.kind !== d.kind) return;
      e.preventDefault();
      e.dataTransfer.dropEffect = 'move';
      const box = e.currentTarget.getBoundingClientRect();
      const after = e.clientY > box.top + box.height / 2;
      // Dropped next to itself, it stays where it is
      const order = docs.documents.filter((x) => x.kind === d.kind);
      const target = order.indexOf(d) + (after ? 1 : 0);
      const from = order.indexOf(dragged);
      const stays = target === from || target === from + 1;
      if (stays) setDrop(null);
      else if (drop?.id !== d.id || drop.after !== after) setDrop({ id: d.id, after });
    },
    onDrop: (e: DragEvent<HTMLDivElement>) => {
      e.preventDefault();
      if (dragged && drop) dispatch({ type: 'move', id: dragged.id, targetId: drop.id, after: drop.after });
      endDrag();
    },
    onDragEnd: endDrag,
  });

  const { unseenErrors } = useSyncExternalStore(subscribeLogs, getLogSnapshot);
  const unsaved = useSyncExternalStore(subscribeUnsaved, getUnsaved);

  const singleton = (kind: SingletonKind, badge?: number) => (
    <NavItem
      badge={badge}
      icon={ICONS[kind]}
      label={documentTitle({ kind, id: kind })}
      collapsed={collapsed}
      active={docs.activeId === kind}
      onClick={() => dispatch({ type: 'openSingleton', kind })}
    />
  );

  const section = (title: string, items: MorphyDocument[]) => (
    <>
      {collapsed ? <div className="nav-sep" /> : <div className="nav-section-title">{title}</div>}
      {items.length === 0 && !collapsed && <div className="nav-empty">None open</div>}
      {items.map((d) => (
        <NavItem
          key={d.id}
          icon={ICONS[d.kind]}
          label={documentTitle(d)}
          collapsed={collapsed}
          active={docs.activeId === d.id}
          unsaved={unsaved.has(d.id)}
          onClick={() => dispatch({ type: 'activate', id: d.id })}
          onClose={() => void closeDocuments([d], dispatch)}
          drag={dragOf(d)}
        />
      ))}
    </>
  );

  const onResizeStart = (e: ReactPointerEvent<HTMLDivElement>) => {
    e.preventDefault();
    const handle = e.currentTarget;
    handle.setPointerCapture(e.pointerId);
    const startX = e.clientX;
    const startWidth = width;
    let current = startWidth;
    const move = (ev: PointerEvent) => {
      current = Math.min(MAX_EXPANDED, Math.max(COLLAPSED_WIDTH, startWidth + ev.clientX - startX));
      setDragWidth(current);
    };
    const up = () => {
      handle.removeEventListener('pointermove', move);
      handle.removeEventListener('pointerup', up);
      setDragWidth(null);
      if (current < SNAP_BELOW) onChange({ ...state, collapsed: true });
      else onChange({ collapsed: false, width: Math.max(MIN_EXPANDED, current) });
    };
    handle.addEventListener('pointermove', move);
    handle.addEventListener('pointerup', up);
  };

  return (
    <aside className={`navigator${collapsed ? ' collapsed' : ''}`} style={{ width }}>
      <div className="nav-head">
        {collapsed ? (
          // Collapsed, the logo is all there is room for, and expands the navigator again
          <button
            className="nav-logo-button"
            title="Expand the navigator (Alt+B)"
            onClick={() => onChange({ ...state, collapsed: false })}
          >
            <img className="nav-logo" src="/morphy-icon.png" alt="Morphy" />
          </button>
        ) : (
          <>
            <img className="nav-logo" src="/morphy-icon.png" alt="" />
            <span className="nav-brand">Morphy</span>
            <button
              className="nav-toggle"
              title="Collapse to icons (Alt+B)"
              onClick={() => onChange({ ...state, collapsed: true })}
            >
              «
            </button>
          </>
        )}
      </div>
      <div className="nav-body">
        {singleton('home')}
        {singleton('databases')}
        <NavItem icon="+" label="New Board" collapsed={collapsed} onClick={() => dispatch({ type: 'openBoard' })} />
        {section('Databases', databases)}
        {section('Boards', boards)}
      </div>
      <div className="nav-foot">
        <NavItem
          icon="✕"
          label="Close All"
          collapsed={collapsed}
          disabled={boards.length === 0}
          onClick={() => void closeDocuments(boards, dispatch)}
        />
        {singleton('logs', unseenErrors)}
        <NavItem icon="⚙" label="Settings" collapsed={collapsed} onClick={() => openSettings()} />
      </div>
      <div className="nav-resize" onPointerDown={onResizeStart} title="Drag to resize; narrow it to collapse to icons" />
    </aside>
  );
}

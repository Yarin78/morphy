import { type PointerEvent as ReactPointerEvent, useState } from 'react';
import { documentTitle, type MorphyDocument, type SingletonKind } from './documents';
import { useDocuments } from './documentsStore';

// The navigator on the left: the singleton documents, New Board, the open databases and
// boards, and Close All and Settings at the bottom. It collapses to icons, and resizes by
// dragging its right edge; dragged narrow enough, it snaps to icons.

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
  settings: '⚙',
  database: '▤',
  board: '♞',
};

function NavItem({
  icon,
  label,
  collapsed,
  active,
  disabled,
  onClick,
  onClose,
}: {
  icon: string;
  label: string;
  collapsed: boolean;
  active?: boolean;
  disabled?: boolean;
  onClick: () => void;
  onClose?: () => void;
}) {
  return (
    <div
      className={`nav-item${active ? ' active' : ''}${disabled ? ' disabled' : ''}`}
      title={collapsed ? label : undefined}
      onClick={disabled ? undefined : onClick}
    >
      <span className="nav-icon">{icon}</span>
      {!collapsed && <span className="nav-label">{label}</span>}
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

  const singleton = (kind: SingletonKind) => (
    <NavItem
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
          onClick={() => dispatch({ type: 'activate', id: d.id })}
          onClose={() => dispatch({ type: 'close', id: d.id })}
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
          onClick={() => dispatch({ type: 'closeAllBoards' })}
        />
        {singleton('settings')}
      </div>
      <div className="nav-resize" onPointerDown={onResizeStart} title="Drag to resize; narrow it to collapse to icons" />
    </aside>
  );
}

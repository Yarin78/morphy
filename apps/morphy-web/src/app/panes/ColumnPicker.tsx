import { type ReactNode, type RefObject, useEffect, useLayoutEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { TbColumns3 } from 'react-icons/tb';

/** The columns of a list to pick from: the search results' columns, or the moves of a position's. */
export interface ColumnChoice {
  all: { key: string; label: string }[];
  shown: { key: string }[];
  /** Whether the columns shown are the default ones, as they're sized by default */
  isDefault: boolean;
}

interface ColumnListProps {
  columns: ColumnChoice;
  onToggle: (key: string) => void;
  onReset: () => void;
  /** What the way back to the default columns is called */
  resetLabel?: string;
}

/** Closes a popup on a press anywhere outside it, or on Escape. */
function useDismiss(ref: RefObject<HTMLElement | null>, open: boolean, onClose: () => void) {
  useEffect(() => {
    if (!open) return;
    const onPointerDown = (e: PointerEvent) => {
      if (!ref.current?.contains(e.target as Node)) onClose();
    };
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    document.addEventListener('pointerdown', onPointerDown);
    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('pointerdown', onPointerDown);
      document.removeEventListener('keydown', onKeyDown);
    };
  }, [ref, open, onClose]);
}

/** The columns of a list, to tick the ones shown, and a way back to the default ones. */
function ColumnList({ columns, onToggle, onReset, resetLabel = 'Default columns and widths' }: ColumnListProps): ReactNode {
  const shown = new Set(columns.shown.map((c) => c.key));
  return (
    <>
      {columns.all.map((c) => (
        <label key={c.key} className="column-picker-item">
          <input type="checkbox" checked={shown.has(c.key)} onChange={() => onToggle(c.key)} />
          {c.label}
        </label>
      ))}
      <div className="menu-sep" />
      <button type="button" className="column-picker-reset" disabled={columns.isDefault} onClick={onReset}>
        {resetLabel}
      </button>
    </>
  );
}

/** A button above the search results opening the list of their columns, to pick the ones shown. */
export function ColumnPicker(props: ColumnListProps) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  useDismiss(ref, open, () => setOpen(false));
  return (
    <div className="column-picker" ref={ref}>
      <button
        type="button"
        className={`search-link column-picker-button${open ? ' open' : ''}`}
        aria-haspopup="true"
        aria-expanded={open}
        title="Pick the columns shown (or right-click the column headers)"
        onClick={() => setOpen(!open)}
      >
        <TbColumns3 /> Columns
      </button>
      {open && (
        <div className="column-picker-list" role="menu">
          <ColumnList {...props} />
        </div>
      )}
    </div>
  );
}

/**
 * The list of the columns, as the column picker has it, where the column headers were
 * right-clicked; kept inside the window.
 */
export function ColumnMenu({ x, y, onClose, ...props }: ColumnListProps & { x: number; y: number; onClose: () => void }) {
  const ref = useRef<HTMLDivElement>(null);
  const [position, setPosition] = useState({ left: x, top: y });
  useDismiss(ref, true, onClose);
  useLayoutEffect(() => {
    const menu = ref.current;
    if (!menu) return;
    const { width, height } = menu.getBoundingClientRect();
    setPosition({
      left: Math.max(4, Math.min(x, window.innerWidth - width - 4)),
      top: Math.max(4, Math.min(y, window.innerHeight - height - 4)),
    });
  }, [x, y]);
  // On the page itself, as a pane's grid may place what's fixed in it by itself
  return createPortal(
    <div className="column-picker-list column-menu" role="menu" ref={ref} style={position}>
      <ColumnList {...props} />
    </div>,
    document.body
  );
}

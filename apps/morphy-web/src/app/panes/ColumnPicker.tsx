import { useEffect, useRef, useState } from 'react';
import { TbColumns3 } from 'react-icons/tb';
import type { ResultColumns } from '../../search/columnLayout';

/** A button above the search results opening the list of their columns, to pick the ones shown. */
export function ColumnPicker({
  columns,
  onToggle,
  onReset,
}: {
  columns: ResultColumns;
  onToggle: (key: string) => void;
  onReset: () => void;
}) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  // A press anywhere else, or Escape, closes the list
  useEffect(() => {
    if (!open) return;
    const onPointerDown = (e: PointerEvent) => {
      if (!ref.current?.contains(e.target as Node)) setOpen(false);
    };
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false);
    };
    document.addEventListener('pointerdown', onPointerDown);
    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('pointerdown', onPointerDown);
      document.removeEventListener('keydown', onKeyDown);
    };
  }, [open]);

  const shown = new Set(columns.shown.map((c) => c.key));
  return (
    <div className="column-picker" ref={ref}>
      <button
        type="button"
        className={`search-link column-picker-button${open ? ' open' : ''}`}
        aria-haspopup="true"
        aria-expanded={open}
        title="Pick the columns shown"
        onClick={() => setOpen(!open)}
      >
        <TbColumns3 /> Columns
      </button>
      {open && (
        <div className="column-picker-list" role="menu">
          {columns.all.map((c) => (
            <label key={c.key} className="column-picker-item">
              <input type="checkbox" checked={shown.has(c.key)} onChange={() => onToggle(c.key)} />
              {c.label}
            </label>
          ))}
          <div className="menu-sep" />
          <button type="button" className="column-picker-reset" disabled={columns.isDefault} onClick={onReset}>
            Default columns and widths
          </button>
        </div>
      )}
    </div>
  );
}

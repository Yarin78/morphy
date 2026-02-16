import { useEffect, useRef, useState } from 'react';

export interface ColumnDef {
  key: string;
  label: string;
}

interface ColumnSelectorProps {
  columns: ColumnDef[];
  hiddenKeys: Set<string>;
  onToggle: (key: string) => void;
}

export function ColumnSelector({
  columns,
  hiddenKeys,
  onToggle,
}: ColumnSelectorProps) {
  const [open, setOpen] = useState(false);
  const buttonRef = useRef<HTMLButtonElement>(null);
  const popoverRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const handler = (e: MouseEvent) => {
      if (
        popoverRef.current &&
        !popoverRef.current.contains(e.target as Node) &&
        buttonRef.current &&
        !buttonRef.current.contains(e.target as Node)
      ) {
        setOpen(false);
      }
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, [open]);

  return (
    <div className="column-selector">
      <button
        ref={buttonRef}
        type="button"
        className="column-selector-btn"
        onClick={() => setOpen(!open)}
        aria-haspopup="true"
        aria-expanded={open}
        title="Show/hide columns"
      >
        Columns
      </button>
      {open && (
        <div
          ref={popoverRef}
          className="column-selector-popover"
          role="menu"
        >
          <div className="column-selector-header">Show columns</div>
          <div className="column-selector-list">
            {columns.map((col) => (
              <label key={col.key} className="column-selector-item">
                <input
                  type="checkbox"
                  checked={!hiddenKeys.has(col.key)}
                  onChange={() => onToggle(col.key)}
                />
                {col.label}
              </label>
            ))}
          </div>
          {hiddenKeys.size > 0 && (
            <div className="column-selector-actions">
              <button
                type="button"
                className="column-selector-action"
                onClick={() => hiddenKeys.forEach((k) => onToggle(k))}
              >
                Show all
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

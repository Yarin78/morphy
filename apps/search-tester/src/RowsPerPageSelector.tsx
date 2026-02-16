import { useEffect, useRef, useState } from 'react';

const ROWS_OPTIONS = [10, 25, 50, 100] as const;

interface RowsPerPageSelectorProps {
  value: number;
  onChange: (value: number) => void;
}

export function RowsPerPageSelector({ value, onChange }: RowsPerPageSelectorProps) {
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
    <div className="rows-per-page-selector">
      <button
        ref={buttonRef}
        type="button"
        className="rows-per-page-btn"
        onClick={() => setOpen(!open)}
        aria-haspopup="true"
        aria-expanded={open}
        title="Rows per page"
      >
        {value} rows
      </button>
      {open && (
        <div ref={popoverRef} className="rows-per-page-popover" role="menu">
          <div className="rows-per-page-header">Rows per page</div>
          <div className="rows-per-page-list">
            {ROWS_OPTIONS.map((n) => (
              <button
                key={n}
                type="button"
                className={`rows-per-page-item ${value === n ? 'selected' : ''}`}
                onClick={() => {
                  onChange(n);
                  setOpen(false);
                }}
              >
                {n}
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

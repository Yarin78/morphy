import { useEffect, useState } from 'react';
import type { DatabaseResponse } from '../api/types';

// The database picked last, picked again by default
const LAST_PICKED_KEY = 'morphy-save-database';

function lastPicked(): string | null {
  try {
    return localStorage.getItem(LAST_PICKED_KEY);
  } catch {
    return null;
  }
}

function rememberPicked(id: string) {
  try {
    localStorage.setItem(LAST_PICKED_KEY, id);
  } catch {
    // ignore, it's a convenience
  }
}

/**
 * Picks the database to save a game in that isn't in one yet: any configured database that
 * isn't read-only. New databases aren't made here.
 */
export function SaveToDatabaseDialog({
  databases,
  onSave,
  onCancel,
}: {
  /** The configured databases, or null while they're loading */
  databases: DatabaseResponse[] | null;
  onSave: (databaseId: string) => void;
  onCancel: () => void;
}) {
  const writable = databases?.filter((db) => !db.readOnly) ?? null;
  const [picked, setPicked] = useState<string | null>(lastPicked);
  // The last one picked if it can still be, else the first
  const selected = writable?.find((db) => db.id === picked)?.id ?? writable?.[0]?.id ?? null;

  const save = () => {
    if (!selected) return;
    rememberPicked(selected);
    onSave(selected);
  };

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onCancel();
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [onCancel]);

  return (
    <div className="dialog-backdrop" onClick={onCancel}>
      <div className="dialog save-dialog" role="dialog" aria-label="Save game" onClick={(e) => e.stopPropagation()}>
        <h2>Save game to database</h2>
        {writable === null && <p className="dialog-note">Loading the databases…</p>}
        {writable?.length === 0 && <p className="dialog-note">No database can be saved to: they're all read-only.</p>}
        {writable && writable.length > 0 && (
          <ul className="database-picker" role="listbox" aria-label="Databases">
            {writable.map((db) => (
              <li
                key={db.id}
                role="option"
                aria-selected={db.id === selected}
                className={db.id === selected ? 'selected' : ''}
                onClick={() => setPicked(db.id)}
                onDoubleClick={() => {
                  rememberPicked(db.id);
                  onSave(db.id);
                }}
              >
                <span className="database-picker-name">{db.displayName}</span>
                <span className="database-picker-path">{db.path}</span>
              </li>
            ))}
          </ul>
        )}
        <div className="dialog-buttons">
          <button className="dialog-secondary" onClick={onCancel}>
            Cancel
          </button>
          <button onClick={save} disabled={!selected} autoFocus>
            Save
          </button>
        </div>
      </div>
    </div>
  );
}

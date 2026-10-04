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
 * Picks the database to save a game to: any configured database that isn't read-only. New
 * databases aren't made here.
 */
export function SaveToDatabaseDialog({
  title,
  databases,
  currentDatabaseId,
  onSave,
  onCancel,
}: {
  title: string;
  /** The configured databases, or null while they're loading */
  databases: DatabaseResponse[] | null;
  /** The game's own database, if it has one: picked first, and marked */
  currentDatabaseId?: string | null;
  onSave: (databaseId: string) => void;
  onCancel: () => void;
}) {
  const writable = databases?.filter((db) => !db.readOnly) ?? null;
  const [picked, setPicked] = useState<string | null>(null);
  const [lastOne] = useState(lastPicked);
  // The one picked, else the game's own, else the one picked last time, else the first: the
  // first of them that can be saved to
  const selected =
    [picked, currentDatabaseId, lastOne]
      .map((id) => writable?.find((db) => db.id === id)?.id)
      .find((id) => id !== undefined) ??
    writable?.[0]?.id ??
    null;

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
      <div className="dialog save-dialog" role="dialog" aria-label={title} onClick={(e) => e.stopPropagation()}>
        <h2>{title}</h2>
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
                <span className="database-picker-name">
                  {db.displayName}
                  {db.id === currentDatabaseId && <span className="database-picker-current">this game's database</span>}
                </span>
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

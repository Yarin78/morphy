import { useEffect, useState } from 'react';
import { fetchDatabases } from '../../api/client';
import type { DatabaseResponse } from '../../api/types';
import { useDocuments } from '../documentsStore';

function formatOf(path: string): string {
  if (path.endsWith('.2cbh')) return 'ChessBase (new)';
  if (path.endsWith('.cbh')) return 'ChessBase';
  if (path.endsWith('.pgn')) return 'PGN';
  return '';
}

/** The databases configured in morphy-service; clicking one opens it. */
export function DatabasesPane() {
  const { dispatch } = useDocuments();
  const [databases, setDatabases] = useState<DatabaseResponse[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchDatabases()
      .then((res) => setDatabases(res.databases))
      .catch((err: unknown) => setError(err instanceof Error ? err.message : String(err)));
  }, []);

  return (
    <div className="pane-page">
      <h1>All Databases</h1>
      {error && <p className="pane-error">{error}</p>}
      {!databases && !error && <p className="pane-muted">Loading…</p>}
      {databases?.length === 0 && <p className="pane-muted">No databases are configured.</p>}
      {databases && databases.length > 0 && (
        <table className="database-table">
          <thead>
            <tr>
              <th>Name</th>
              <th>Format</th>
              <th>Path</th>
            </tr>
          </thead>
          <tbody>
            {databases.map((db) => (
              <tr
                key={db.id}
                onClick={() => dispatch({ type: 'openDatabase', databaseId: db.id, name: db.displayName })}
                title="Open the database"
              >
                <td className="database-name">{db.displayName}</td>
                <td>{formatOf(db.path)}</td>
                <td className="database-path">{db.path}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}

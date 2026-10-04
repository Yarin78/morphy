import { DatabaseSearch } from '../../database/DatabaseSearch';
import { useDocument, useDocuments } from '../documentsStore';

/** A database's games and entities, searched; clicking a game opens it on a board. */
export function DatabasePane() {
  const doc = useDocument('database');
  const { dispatch } = useDocuments();
  return (
    <div className="pane-scroll">
      <DatabaseSearch
        databaseId={doc.databaseId}
        onOpenGame={(gameId) => dispatch({ type: 'openBoard', databaseId: doc.databaseId, gameId })}
      />
    </div>
  );
}

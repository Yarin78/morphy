import { useDocument } from '../documentsStore';
import { useEntity } from '../entityStore';
import { EntityDetails } from './PreviewPane';
import { Results } from './SearchPane';

/** The top of an entity's document: its details, as its preview in the database's search. */
export function EntityDetailsPane() {
  const doc = useDocument('entity');
  const entity = useEntity();
  if (entity.kind === 'loading') return <div className="preview-empty">Loading…</div>;
  if (entity.kind === 'error') return <div className="preview-empty pane-error">{entity.message}</div>;
  return <EntityDetails kind={doc.entityKind} entity={entity.entity} />;
}

/** The games of an entity's document, sorted and picked from as a database's game results. */
export function EntityGamesPane() {
  return (
    <div className="search-pane">
      <Results />
    </div>
  );
}

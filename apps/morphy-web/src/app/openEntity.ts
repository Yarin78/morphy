import { entityTitle } from '../search/columns';
import type { DocumentsAction, EntityKind } from './documents';

/** Opens an entity found by a search in a document of its own, or shows the one it's open in. */
export function openEntityAction(databaseId: string, kind: EntityKind, entity: Record<string, unknown>): DocumentsAction {
  return {
    type: 'openEntity',
    databaseId,
    entityKind: kind,
    entityId: entity.id as number,
    title: entityTitle(kind, entity),
  };
}

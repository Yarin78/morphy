import { createContext, useContext } from 'react';

/** The entity of an entity document, as fetched for its details pane. */
export type EntityState =
  | { kind: 'loading' }
  | { kind: 'loaded'; entity: Record<string, unknown> }
  | { kind: 'error'; message: string };

export const EntityContext = createContext<EntityState | null>(null);

export function useEntity(): EntityState {
  const state = useContext(EntityContext);
  if (!state) throw new Error('useEntity outside an entity document');
  return state;
}

import { createContext, useContext } from 'react';
import type { SerializedDockview } from 'dockview-react';
import type { DocumentsAction, DocumentsState, MorphyDocument } from './documents';

export interface DocumentsStore {
  state: DocumentsState;
  dispatch: (action: DocumentsAction) => void;
  /** The saved grid layout of a document, if it has one */
  layoutOf: (id: string) => SerializedDockview | undefined;
  /** Keeps a document's grid layout. It's saved with the documents but doesn't re-render. */
  setLayout: (id: string, layout: SerializedDockview) => void;
}

export const DocumentsContext = createContext<DocumentsStore | null>(null);

export function useDocuments(): DocumentsStore {
  const store = useContext(DocumentsContext);
  if (!store) throw new Error('useDocuments outside DocumentsProvider');
  return store;
}

/** Whether the document a pane belongs to is the one shown, set around each document's grid. */
export const DocumentActiveContext = createContext(false);

export function useDocumentActive(): boolean {
  return useContext(DocumentActiveContext);
}

/** The document a pane belongs to, set around each document's grid. */
export const DocumentContext = createContext<MorphyDocument | null>(null);

export function useDocument<K extends MorphyDocument['kind']>(kind: K): Extract<MorphyDocument, { kind: K }> {
  const doc = useContext(DocumentContext);
  if (!doc || doc.kind !== kind) throw new Error(`Not in a ${kind} document`);
  return doc as Extract<MorphyDocument, { kind: K }>;
}

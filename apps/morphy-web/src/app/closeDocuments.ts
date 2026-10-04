import { documentTitle, type DocumentsAction, type MorphyDocument } from './documents';
import { askAboutUnsavedChanges } from './unsavedQuestion';
import { deleteDraft } from './drafts';
import { getUnsaved, saverOf } from './unsavedStore';

/**
 * Closes documents, one at a time. A board with unsaved changes is shown and asked about first:
 * saved, its changes thrown away, or the closing stopped there, leaving it and the rest open.
 */
export async function closeDocuments(docs: MorphyDocument[], dispatch: (action: DocumentsAction) => void) {
  for (const doc of docs) {
    if (doc.kind === 'board' && getUnsaved().has(doc.id)) {
      dispatch({ type: 'activate', id: doc.id });
      const saver = saverOf(doc.id);
      const choice = await askAboutUnsavedChanges(documentTitle(doc), saver?.canSave ?? false);
      if (choice === 'cancel') return;
      if (choice !== 'discard' && !(await saver?.save(choice))) return;
    }
    dispatch({ type: 'close', id: doc.id });
    // Its unsaved changes are let go, or were just saved
    deleteDraft(doc.id);
  }
}

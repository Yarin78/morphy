import { useEffect, useSyncExternalStore } from 'react';
import { TbAlertCircle } from 'react-icons/tb';
import { getQuestion, subscribeQuestion } from './unsavedQuestion';

/** The question while one is asked. */
export function UnsavedChangesDialog() {
  const q = useSyncExternalStore(subscribeQuestion, getQuestion);

  useEffect(() => {
    if (!q) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key !== 'Escape' && e.key !== 'Enter') return;
      e.preventDefault();
      e.stopPropagation();
      if (e.key === 'Escape') q.answer('cancel');
      else q.answer(q.canSave ? 'save' : 'saveAs');
    };
    // Before the other handlers, so the keys don't reach the board behind
    document.addEventListener('keydown', onKey, { capture: true });
    return () => document.removeEventListener('keydown', onKey, { capture: true });
  }, [q]);

  if (!q) return null;
  return (
    <div className="dialog-backdrop">
      <div className="dialog unsaved-dialog" role="alertdialog" aria-label="Unsaved changes">
        <div className="error-dialog-head">
          <TbAlertCircle className="unsaved-dialog-icon" />
          <h2>Save the changes to “{q.title}”?</h2>
        </div>
        <p className="error-dialog-message">
          {q.canSave
            ? 'Your changes will be lost if you close the board without saving them.'
            : "Your changes will be lost if you close the board without saving them. Its database is read-only, so they can only be saved as a new game in another one."}
        </p>
        <div className="dialog-buttons">
          <button className="dialog-secondary dialog-left" onClick={() => q.answer('discard')}>
            Don’t Save
          </button>
          <button className="dialog-secondary" onClick={() => q.answer('cancel')}>
            Cancel
          </button>
          <button className={q.canSave ? 'dialog-secondary' : ''} onClick={() => q.answer('saveAs')}>
            Save As…
          </button>
          {q.canSave && <button onClick={() => q.answer('save')}>Save</button>}
        </div>
      </div>
    </div>
  );
}

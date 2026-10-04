import { useEffect, useSyncExternalStore } from 'react';
import { TbAlertTriangle } from 'react-icons/tb';
import { focusRequest } from '../logs/logStore';
import { useDocuments } from './documentsStore';
import { dismissError, getErrors, subscribeErrors } from './errorStore';

/** The dialog telling the user that an operation failed: one at a time, the oldest first. */
export function ErrorDialog() {
  const errors = useSyncExternalStore(subscribeErrors, getErrors);
  const { dispatch } = useDocuments();
  const error = errors[0];

  useEffect(() => {
    if (!error) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape' || e.key === 'Enter') {
        e.preventDefault();
        e.stopPropagation();
        dismissError();
      }
    };
    // Before the other handlers, so the keys don't reach the board behind
    document.addEventListener('keydown', onKey, { capture: true });
    return () => document.removeEventListener('keydown', onKey, { capture: true });
  }, [error]);

  if (!error) return null;

  const showInLogs = () => {
    focusRequest(error.requestId);
    dispatch({ type: 'openSingleton', kind: 'logs' });
    dismissError();
  };

  return (
    <div className="dialog-backdrop">
      <div className="dialog error-dialog" role="alertdialog" aria-label={error.title}>
        <div className="error-dialog-head">
          <TbAlertTriangle className="error-dialog-icon" />
          <h2>{error.title}</h2>
        </div>
        <p className="error-dialog-message">{error.message}</p>
        {errors.length > 1 && <p className="dialog-note">{errors.length - 1} more after this one</p>}
        <div className="dialog-buttons">
          <button className="dialog-secondary" onClick={showInLogs}>
            Show in Logs
          </button>
          <button onClick={dismissError} autoFocus>
            OK
          </button>
        </div>
      </div>
    </div>
  );
}

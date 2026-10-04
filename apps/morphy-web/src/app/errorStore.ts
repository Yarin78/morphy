import { ApiError } from '../api/client';

// The failures to tell the user about, in a dialog, oldest first

export interface AppError {
  /** What failed, e.g. 'Saving the game failed' */
  title: string;
  /** Why: the service's message, if it gave one */
  message: string;
  /** The call that failed, to show in the logs */
  requestId: string | null;
}

let errors: readonly AppError[] = [];
const listeners = new Set<() => void>();

function set(next: readonly AppError[]) {
  errors = next;
  listeners.forEach((l) => l());
}

export function subscribeErrors(listener: () => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function getErrors(): readonly AppError[] {
  return errors;
}

/** Tells the user that an operation failed, and why. */
export function showError(title: string, err: unknown) {
  const message =
    err instanceof ApiError
      ? (err.serverMessage ?? err.message)
      : err instanceof Error
        ? err.message
        : String(err);
  set([...errors, { title, message, requestId: err instanceof ApiError ? err.requestId : null }]);
}

/** The oldest error has been read. */
export function dismissError() {
  set(errors.slice(1));
}

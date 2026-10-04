// Brief notices of things that went well, like a saved game: shown for a moment, then gone

export interface Toast {
  id: number;
  message: string;
}

/** How long a notice is shown, in ms, before it fades out */
export const TOAST_MS = 2500;

let toasts: readonly Toast[] = [];
let nextId = 1;
const listeners = new Set<() => void>();

function set(next: readonly Toast[]) {
  toasts = next;
  listeners.forEach((l) => l());
}

export function subscribeToasts(listener: () => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function getToasts(): readonly Toast[] {
  return toasts;
}

/** Shows a notice for a moment. */
export function showToast(message: string) {
  const id = nextId++;
  set([...toasts, { id, message }]);
  setTimeout(() => set(toasts.filter((t) => t.id !== id)), TOAST_MS);
}

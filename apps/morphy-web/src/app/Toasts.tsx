import { useSyncExternalStore } from 'react';
import { TbCircleCheck } from 'react-icons/tb';
import { getToasts, subscribeToasts, TOAST_MS } from './toastStore';

/** The notices shown, in the bottom right corner, each fading out after a moment. */
export function Toasts() {
  const toasts = useSyncExternalStore(subscribeToasts, getToasts);
  return (
    <div className="toasts" role="status" aria-live="polite">
      {toasts.map((toast) => (
        <div key={toast.id} className="toast" style={{ animationDuration: `${TOAST_MS}ms` }}>
          <TbCircleCheck className="toast-icon" />
          {toast.message}
        </div>
      ))}
    </div>
  );
}

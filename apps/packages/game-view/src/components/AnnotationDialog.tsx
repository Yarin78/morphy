import { useEffect } from 'react';
import type { ReactNode } from 'react';
import './GameInfoDialog.css';
import './AnnotationDialog.css';

interface AnnotationDialogProps {
  title: string;
  /** Keeps what was chosen; OK and Enter. */
  onSubmit: () => void;
  /** Closes the dialog leaving the move as it was; Cancel, Escape and a click outside. */
  onCancel: () => void;
  /** When given, a Remove button that takes the annotation away. */
  onRemove?: () => void;
  /** Whether OK can't be chosen, as when a field isn't valid. */
  invalid?: boolean;
  children: ReactNode;
}

/** A small dialog for an annotation of a move, with the look of the game info dialog. */
export const AnnotationDialog: React.FC<AnnotationDialogProps> = ({
  title,
  onSubmit,
  onCancel,
  onRemove,
  invalid = false,
  children,
}) => {
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onCancel();
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [onCancel]);

  return (
    <div className="game-info-overlay" onMouseDown={onCancel}>
      <form
        className="game-info-dialog annotation-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="annotation-dialog-title"
        onMouseDown={(e) => e.stopPropagation()}
        onSubmit={(e) => {
          e.preventDefault();
          if (!invalid) onSubmit();
        }}
        noValidate
      >
        <h3 id="annotation-dialog-title">{title}</h3>
        {children}
        <div className="game-info-buttons">
          {onRemove && (
            <button type="button" className="game-info-cancel entity-left" onClick={onRemove}>
              Remove
            </button>
          )}
          <button type="button" className="game-info-cancel" onClick={onCancel}>
            Cancel
          </button>
          <button type="submit" className="game-info-ok" disabled={invalid}>
            OK
          </button>
        </div>
      </form>
    </div>
  );
};

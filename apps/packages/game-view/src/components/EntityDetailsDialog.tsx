import { useEffect, useRef, useState } from 'react';
import './GameInfoDialog.css';

/** An entity with details that games share, like a tournament or a source. */
export interface SharedEntity {
  id: number | null;
  /** The number of games of an existing one. */
  gameCount?: number;
}

type FormValues = Record<string, string | boolean>;

/** What an entity's fields need to render themselves in the dialog. */
export interface EntityFieldsContext<F extends FormValues> {
  form: F;
  set: (field: keyof F & string) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => void;
  readOnly: boolean;
  errors: Partial<Record<keyof F, string>>;
  /** A text input with its label above it; the first field gets the focus while editing. */
  input: (
    field: keyof F & string,
    label: string,
    props?: React.InputHTMLAttributes<HTMLInputElement> & { first?: boolean }
  ) => React.ReactNode;
}

interface EntityDetailsDialogProps<E extends SharedEntity, F extends FormValues> {
  entity: E;
  /** What it is, in lowercase: "tournament". */
  what: string;
  /** Changes an existing one, for every game in it; without it, it can't be. */
  update?: (entity: E) => Promise<E>;
  toForm: (entity: E) => F;
  validate: (form: F) => Partial<Record<keyof F, string>>;
  fromForm: (form: F, base: E, id: number | null) => E;
  /** How a new one based on an existing one starts out; by default with the same details. */
  basedOn?: (form: F) => F;
  renderFields: (context: EntityFieldsContext<F>) => React.ReactNode;
  /** Called with what the game should now refer to. */
  onApply: (entity: E) => void;
  onClose: () => void;
}

type Mode = 'new' | 'existing' | 'editing-existing';

/**
 * The details of something the game refers to that other games share, like its tournament or
 * source. A new one's details are edited freely. An existing one's are read-only, since they belong
 * to all its games: it can instead be the starting point of a new one (say, next year's edition), or,
 * deliberately, be changed for all its games, which is saved straight away.
 */
export function EntityDetailsDialog<E extends SharedEntity, F extends FormValues>({
  entity,
  what,
  update,
  toForm,
  validate,
  fromForm,
  basedOn,
  renderFields,
  onApply,
  onClose,
}: EntityDetailsDialogProps<E, F>) {
  const [mode, setMode] = useState<Mode>(entity.id != null ? 'existing' : 'new');
  const [form, setForm] = useState<F>(() => toForm(entity));
  const [errors, setErrors] = useState<Partial<Record<keyof F, string>>>({});
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const firstRef = useRef<HTMLInputElement>(null);
  const closeRef = useRef<HTMLButtonElement>(null);

  const readOnly = mode === 'existing';
  const What = what.charAt(0).toUpperCase() + what.slice(1);

  useEffect(() => {
    if (readOnly) closeRef.current?.focus();
    else firstRef.current?.focus();
  }, [readOnly]);

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [onClose]);

  const set = (field: keyof F & string) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
    const value = e.target instanceof HTMLInputElement && e.target.type === 'checkbox' ? e.target.checked : e.target.value;
    setForm((current) => ({ ...current, [field]: value }));
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (readOnly || saving) return;
    const found = validate(form);
    setErrors(found);
    if (Object.keys(found).length > 0) return;
    if (mode === 'new') {
      onApply(fromForm(form, entity, null));
      onClose();
      return;
    }
    // Changing the existing one, for all its games
    if (!update || entity.id == null) return;
    setSaving(true);
    setSaveError(null);
    try {
      const saved = await update(fromForm(form, entity, entity.id));
      onApply(saved);
      setForm(toForm(saved));
      setMode('existing');
    } catch (err) {
      setSaveError(err instanceof Error ? err.message : String(err));
    } finally {
      setSaving(false);
    }
  };

  const basedOnThis = () => {
    if (basedOn) setForm(basedOn);
    setMode('new');
    setErrors({});
  };

  const input: EntityFieldsContext<F>['input'] = (field, label, { first, ...props } = {}) => (
    <label className={`game-info-field ${what}-field-${field}`}>
      <span className="game-info-label">{label}</span>
      <input
        type="text"
        ref={first ? firstRef : undefined}
        value={String(form[field])}
        onChange={set(field)}
        readOnly={readOnly}
        aria-invalid={errors[field] ? true : undefined}
        autoComplete="off"
        data-1p-ignore
        data-lpignore="true"
        {...props}
      />
      {errors[field] && <span className="game-info-error">{errors[field]}</span>}
    </label>
  );

  const games = entity.gameCount;
  const gamesText = games == null ? 'its games' : `its ${games} ${games === 1 ? 'game' : 'games'}`;
  const titleId = `${what}-dialog-title`;

  return (
    <div className="game-info-overlay game-info-overlay-stacked" onMouseDown={onClose}>
      <form
        className={`game-info-dialog entity-dialog ${what}-dialog${readOnly ? ' read-only' : ''}`}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        onMouseDown={(e) => e.stopPropagation()}
        onSubmit={handleSubmit}
        noValidate
      >
        <h3 id={titleId}>
          {What}
          <span className={`entity-badge entity-badge-${mode === 'new' ? 'new' : 'existing'}`}>
            {mode === 'new' ? 'New' : 'Existing'}
          </span>
        </h3>

        {mode === 'existing' && (
          <p className="entity-note">
            An existing {what}, with {gamesText}. Its details belong to the {what}, so they can't be changed for
            this game only.
          </p>
        )}
        {mode === 'editing-existing' && (
          <p className="entity-note entity-warning">
            Changes apply to the {what} and {gamesText}, and are saved as soon as you click Save {what}.
          </p>
        )}

        {renderFields({ form, set, readOnly, errors, input })}

        {saveError && <p className="entity-note entity-error">{saveError}</p>}

        <div className="game-info-buttons">
          {mode === 'existing' && (
            <>
              <button type="button" className="game-info-cancel entity-left" onClick={basedOnThis}>
                New {what} based on this
              </button>
              {update && (
                <button type="button" className="game-info-cancel" onClick={() => setMode('editing-existing')}>
                  Edit {what}…
                </button>
              )}
              <button type="button" className="game-info-ok" onClick={onClose} ref={closeRef}>
                Close
              </button>
            </>
          )}
          {mode === 'new' && (
            <>
              <button type="button" className="game-info-cancel" onClick={onClose}>
                Cancel
              </button>
              <button type="submit" className="game-info-ok">
                OK
              </button>
            </>
          )}
          {mode === 'editing-existing' && (
            <>
              <button
                type="button"
                className="game-info-cancel"
                onClick={() => {
                  setForm(toForm(entity));
                  setErrors({});
                  setSaveError(null);
                  setMode('existing');
                }}
              >
                Cancel
              </button>
              <button type="submit" className="game-info-ok entity-danger" disabled={saving}>
                {saving ? 'Saving…' : `Save ${what}`}
              </button>
            </>
          )}
        </div>
      </form>
    </div>
  );
}

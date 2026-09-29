import { useEffect, useRef, useState } from 'react';
import { TIME_CONTROLS, TOURNAMENT_TYPES } from '../utils/tournament';
import type { DateParts, TournamentInfo, TournamentService } from '../utils/tournament';
import './GameInfoDialog.css';

interface TournamentDialogProps {
  tournament: TournamentInfo;
  service?: TournamentService;
  /** Called with the tournament the game should now be in. */
  onApply: (tournament: TournamentInfo) => void;
  onClose: () => void;
}

/**
 * The details of the game's tournament. A new tournament's details are edited freely. An existing
 * tournament's are read-only, since the tournament belongs to all its games: it can instead be the
 * starting point of a new tournament (say, next year's edition), or, deliberately, be changed for
 * all its games, which is saved straight away.
 */
type Mode = 'new' | 'existing' | 'editing-existing';

interface Form {
  title: string;
  startYear: string;
  startMonth: string;
  startDay: string;
  endYear: string;
  endMonth: string;
  endDay: string;
  place: string;
  nation: string;
  type: string;
  timeControl: string;
  rounds: string;
  category: string;
  complete: boolean;
  teamTournament: boolean;
}

type Errors = Partial<Record<keyof Form, string>>;

const part = (value: number | undefined) => (value ? String(value) : '');

function toForm(t: TournamentInfo): Form {
  return {
    title: t.title,
    startYear: part(t.startDate?.year),
    startMonth: part(t.startDate?.month),
    startDay: part(t.startDate?.day),
    endYear: part(t.endDate?.year),
    endMonth: part(t.endDate?.month),
    endDay: part(t.endDate?.day),
    place: t.place ?? '',
    nation: t.nation ?? '',
    type: t.type ?? '',
    timeControl: t.timeControl ?? '',
    rounds: part(t.rounds),
    category: part(t.category),
    complete: Boolean(t.complete),
    teamTournament: Boolean(t.teamTournament),
  };
}

function validate(form: Form): Errors {
  const errors: Errors = {};
  const number = (field: keyof Form, min: number, max: number) => {
    const value = String(form[field]).trim();
    if (value && !(/^\d+$/.test(value) && +value >= min && +value <= max)) {
      errors[field] = `Must be ${min}–${max}`;
    }
  };
  if (!form.title.trim()) errors.title = 'Needs a name';
  number('startYear', 1, 9999);
  number('startMonth', 1, 12);
  number('startDay', 1, 31);
  number('endYear', 1, 9999);
  number('endMonth', 1, 12);
  number('endDay', 1, 31);
  number('rounds', 1, 255);
  number('category', 1, 255);
  if (form.nation.trim() && !/^[A-Za-z0-9]{3}$/.test(form.nation.trim())) {
    errors.nation = 'A 3-letter code';
  }
  return errors;
}

function fromForm(form: Form, base: TournamentInfo, id: number | null): TournamentInfo {
  const n = (value: string) => (value.trim() ? parseInt(value, 10) : undefined);
  const date = (y: string, m: string, d: string): DateParts | undefined =>
    n(y) || n(m) || n(d) ? { year: n(y) ?? 0, month: n(m) ?? 0, day: n(d) ?? 0 } : undefined;
  return {
    id,
    title: form.title.trim(),
    startDate: date(form.startYear, form.startMonth, form.startDay),
    endDate: date(form.endYear, form.endMonth, form.endDay),
    place: form.place.trim() || undefined,
    nation: form.nation.trim().toUpperCase() || undefined,
    type: form.type || undefined,
    timeControl: form.timeControl || undefined,
    rounds: n(form.rounds),
    category: n(form.category),
    complete: form.complete || undefined,
    teamTournament: form.teamTournament || undefined,
    gameCount: id != null ? base.gameCount : undefined,
  };
}

export const TournamentDialog: React.FC<TournamentDialogProps> = ({ tournament, service, onApply, onClose }) => {
  const [mode, setMode] = useState<Mode>(tournament.id != null ? 'existing' : 'new');
  const [form, setForm] = useState<Form>(() => toForm(tournament));
  const [errors, setErrors] = useState<Errors>({});
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const titleRef = useRef<HTMLInputElement>(null);
  const closeRef = useRef<HTMLButtonElement>(null);

  const readOnly = mode === 'existing';

  useEffect(() => {
    if (readOnly) closeRef.current?.focus();
    else titleRef.current?.focus();
  }, [readOnly]);

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [onClose]);

  const set = (field: keyof Form) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
    const value = e.target instanceof HTMLInputElement && e.target.type === 'checkbox' ? e.target.checked : e.target.value;
    setForm((current) => ({ ...current, [field]: value }));
  };

  const check = (): boolean => {
    const found = validate(form);
    setErrors(found);
    return Object.keys(found).length === 0;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (readOnly || saving || !check()) return;
    if (mode === 'new') {
      onApply(fromForm(form, tournament, null));
      onClose();
      return;
    }
    // Changing the existing tournament, for all its games
    if (!service || tournament.id == null) return;
    setSaving(true);
    setSaveError(null);
    try {
      const saved = await service.update(fromForm(form, tournament, tournament.id));
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
    // Keep everything but the link to the existing tournament, which the dates are the likely
    // thing to change from. A new tournament doesn't have all its games in the database yet.
    setForm((current) => ({ ...current, complete: false }));
    setMode('new');
    setErrors({});
  };

  const input = (field: keyof Form, label: string, props: React.InputHTMLAttributes<HTMLInputElement> & { ref?: React.Ref<HTMLInputElement> } = {}) => (
    <label className={`game-info-field tournament-field-${field}`}>
      {label && <span className="game-info-label">{label}</span>}
      <input
        type="text"
        value={String(form[field])}
        onChange={set(field)}
        readOnly={readOnly}
        aria-label={label ? undefined : field}
        aria-invalid={errors[field] ? true : undefined}
        autoComplete="off"
        data-1p-ignore
        data-lpignore="true"
        {...props}
      />
      {errors[field] && <span className="game-info-error">{errors[field]}</span>}
    </label>
  );
  const numberProps = { inputMode: 'numeric' as const };
  const games = tournament.gameCount;
  const gamesText = games == null ? 'its games' : `its ${games} ${games === 1 ? 'game' : 'games'}`;

  return (
    <div className="game-info-overlay game-info-overlay-stacked" onMouseDown={onClose}>
      <form
        className={`game-info-dialog tournament-dialog${readOnly ? ' read-only' : ''}`}
        role="dialog"
        aria-modal="true"
        aria-labelledby="tournament-dialog-title"
        onMouseDown={(e) => e.stopPropagation()}
        onSubmit={handleSubmit}
        noValidate
      >
        <h3 id="tournament-dialog-title">
          Tournament
          <span className={`tournament-badge tournament-badge-${mode === 'new' ? 'new' : 'existing'}`}>
            {mode === 'new' ? 'New' : 'Existing'}
          </span>
        </h3>

        {mode === 'existing' && (
          <p className="tournament-note">
            An existing tournament, with {gamesText}. Its details belong to the tournament, so they can't be
            changed for this game only.
          </p>
        )}
        {mode === 'editing-existing' && (
          <p className="tournament-note tournament-warning">
            Changes apply to the tournament and {gamesText}, and are saved as soon as you click Save
            tournament.
          </p>
        )}

        <fieldset className="game-info-row">
          {input('title', 'Name', { ref: titleRef })}
          {input('place', 'Site')}
          {input('nation', 'Nation', { placeholder: 'IOC', maxLength: 3 })}
        </fieldset>

        <div className="game-info-pair">
          <fieldset className="game-info-row">
            <legend>Start</legend>
            {input('startYear', 'Year', { ...numberProps, placeholder: 'yyyy' })}
            {input('startMonth', 'Month', { ...numberProps, placeholder: 'mm' })}
            {input('startDay', 'Day', { ...numberProps, placeholder: 'dd' })}
          </fieldset>
          <fieldset className="game-info-row">
            <legend>End</legend>
            {input('endYear', 'Year', { ...numberProps, placeholder: 'yyyy' })}
            {input('endMonth', 'Month', { ...numberProps, placeholder: 'mm' })}
            {input('endDay', 'Day', { ...numberProps, placeholder: 'dd' })}
          </fieldset>
        </div>

        <fieldset className="game-info-row">
          <legend>Format</legend>
          <label className="game-info-field">
            <span className="game-info-label">Type</span>
            <select value={form.type} onChange={set('type')} disabled={readOnly}>
              <option value=""></option>
              {TOURNAMENT_TYPES.map((t) => (
                <option key={t.value} value={t.value}>
                  {t.label}
                </option>
              ))}
            </select>
          </label>
          <label className="game-info-field">
            <span className="game-info-label">Time control</span>
            <select value={form.timeControl} onChange={set('timeControl')} disabled={readOnly}>
              <option value="">Normal</option>
              {TIME_CONTROLS.map((t) => (
                <option key={t.value} value={t.value}>
                  {t.label}
                </option>
              ))}
            </select>
          </label>
          {input('rounds', 'Rounds', numberProps)}
          {input('category', 'Category', numberProps)}
        </fieldset>

        <fieldset className="game-info-row tournament-flags">
          <legend>Flags</legend>
          <label className="tournament-checkbox">
            <input type="checkbox" checked={form.complete} onChange={set('complete')} disabled={readOnly} />
            Complete
          </label>
          <label className="tournament-checkbox">
            <input type="checkbox" checked={form.teamTournament} onChange={set('teamTournament')} disabled={readOnly} />
            Team tournament
          </label>
        </fieldset>

        {saveError && <p className="tournament-note tournament-error">{saveError}</p>}

        <div className="game-info-buttons">
          {mode === 'existing' && (
            <>
              <button type="button" className="game-info-cancel tournament-left" onClick={basedOnThis}>
                New tournament based on this
              </button>
              {service && (
                <button type="button" className="game-info-cancel" onClick={() => setMode('editing-existing')}>
                  Edit tournament…
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
                  setForm(toForm(tournament));
                  setErrors({});
                  setSaveError(null);
                  setMode('existing');
                }}
              >
                Cancel
              </button>
              <button type="submit" className="game-info-ok tournament-danger" disabled={saving}>
                {saving ? 'Saving…' : 'Save tournament'}
              </button>
            </>
          )}
        </div>
      </form>
    </div>
  );
};

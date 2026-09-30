import { TIME_CONTROLS, TOURNAMENT_TYPES } from '../utils/tournament';
import { dateTextError, formatDateText, parseDateText } from '../utils/dateText';
import type { TournamentInfo, TournamentService } from '../utils/tournament';
import { DateField } from './DateField';
import { EntityDetailsDialog } from './EntityDetailsDialog';

interface TournamentDialogProps {
  tournament: TournamentInfo;
  service?: TournamentService;
  /** Called with the tournament the game should now be in. */
  onApply: (tournament: TournamentInfo) => void;
  onClose: () => void;
}

type Form = {
  title: string;
  start: string;
  end: string;
  place: string;
  nation: string;
  type: string;
  timeControl: string;
  rounds: string;
  category: string;
  complete: boolean;
  teamTournament: boolean;
};

type Errors = Partial<Record<keyof Form, string>>;

const part = (value: number | undefined) => (value ? String(value) : '');

function toForm(t: TournamentInfo): Form {
  return {
    title: t.title,
    start: formatDateText(t.startDate),
    end: formatDateText(t.endDate),
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
  for (const field of ['start', 'end'] as const) {
    const error = dateTextError(form[field]);
    if (error) errors[field] = error;
  }
  number('rounds', 1, 255);
  number('category', 1, 255);
  if (form.nation.trim() && !/^[A-Za-z0-9]{3}$/.test(form.nation.trim())) {
    errors.nation = 'A 3-letter code';
  }
  return errors;
}

function fromForm(form: Form, base: TournamentInfo, id: number | null): TournamentInfo {
  const n = (value: string) => (value.trim() ? parseInt(value, 10) : undefined);
  return {
    id,
    title: form.title.trim(),
    startDate: parseDateText(form.start) ?? undefined,
    endDate: parseDateText(form.end) ?? undefined,
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

/** The details of the game's tournament; see EntityDetailsDialog. */
export const TournamentDialog: React.FC<TournamentDialogProps> = ({ tournament, service, onApply, onClose }) => (
  <EntityDetailsDialog<TournamentInfo, Form>
    entity={tournament}
    what="tournament"
    update={service?.update}
    toForm={toForm}
    validate={validate}
    fromForm={fromForm}
    // The dates are the likely thing to change; and a new tournament doesn't have all its games in
    // the database yet
    basedOn={(form) => ({ ...form, complete: false })}
    onApply={onApply}
    onClose={onClose}
    renderFields={({ form, set, setValue, readOnly, errors, input }) => {
      const numberProps = { inputMode: 'numeric' as const };
      return (
        <>
          <fieldset className="game-info-row">
            {input('title', 'Name', { first: true })}
            {input('place', 'Site')}
            {input('nation', 'Nation', { placeholder: 'IOC', maxLength: 3 })}
          </fieldset>

          <fieldset className="game-info-row">
            <legend>Dates</legend>
            <DateField
              value={form.start}
              onChange={(value) => setValue('start', value)}
              label="Start"
              error={errors.start}
              readOnly={readOnly}
            />
            <DateField
              value={form.end}
              onChange={(value) => setValue('end', value)}
              label="End"
              error={errors.end}
              readOnly={readOnly}
            />
          </fieldset>

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
        </>
      );
    }}
  />
);

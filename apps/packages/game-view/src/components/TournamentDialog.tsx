import { TIME_CONTROLS, TOURNAMENT_TYPES } from '../utils/tournament';
import type { DateParts, TournamentInfo, TournamentService } from '../utils/tournament';
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
};

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
    renderFields={({ form, set, readOnly, input }) => {
      const numberProps = { inputMode: 'numeric' as const };
      return (
        <>
          <fieldset className="game-info-row">
            {input('title', 'Name', { first: true })}
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
        </>
      );
    }}
  />
);

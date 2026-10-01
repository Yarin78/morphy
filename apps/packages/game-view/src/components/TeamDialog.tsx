import type { TeamInfo, TeamService } from '../utils/team';
import { EntityDetailsDialog } from './EntityDetailsDialog';
import { NationSelect } from './NationSelect';

interface TeamDialogProps {
  team: TeamInfo;
  service?: TeamService;
  /** Called with the team the player should now be in. */
  onApply: (team: TeamInfo) => void;
  onClose: () => void;
}

type Form = {
  title: string;
  number: string;
  year: string;
  season: boolean;
  nation: string;
};

const part = (value: number | undefined) => (value ? String(value) : '');

function toForm(t: TeamInfo): Form {
  return {
    title: t.title,
    number: part(t.number),
    year: part(t.year),
    season: Boolean(t.season),
    nation: t.nation ?? '',
  };
}

function validate(form: Form): Partial<Record<keyof Form, string>> {
  const errors: Partial<Record<keyof Form, string>> = {};
  const number = (field: 'number' | 'year', min: number, max: number) => {
    const value = form[field].trim();
    if (value && !(/^\d+$/.test(value) && +value >= min && +value <= max)) {
      errors[field] = `Must be ${min}–${max}`;
    }
  };
  if (!form.title.trim()) errors.title = 'Needs a title';
  number('number', 1, 9999);
  number('year', 1, 9999);
  return errors;
}

function fromForm(form: Form, base: TeamInfo, id: number | null): TeamInfo {
  const n = (value: string) => (value.trim() ? parseInt(value, 10) : undefined);
  return {
    id,
    title: form.title.trim(),
    number: n(form.number),
    year: n(form.year),
    season: form.season || undefined,
    nation: form.nation || undefined,
    gameCount: id != null ? base.gameCount : undefined,
  };
}

/** The details of a player's team; see EntityDetailsDialog. */
export const TeamDialog: React.FC<TeamDialogProps> = ({ team, service, onApply, onClose }) => (
  <EntityDetailsDialog<TeamInfo, Form>
    entity={team}
    what="team"
    update={service?.update}
    toForm={toForm}
    validate={validate}
    fromForm={fromForm}
    onApply={onApply}
    onClose={onClose}
    renderFields={({ form, set, setValue, readOnly, input }) => (
      <>
        <fieldset className="game-info-row">
          {input('title', 'Title', { first: true })}
          {input('number', 'Number', { inputMode: 'numeric' })}
        </fieldset>
        <fieldset className="game-info-row team-dialog-details">
          {input('year', 'Year', { inputMode: 'numeric', placeholder: 'yyyy' })}
          <label className="tournament-checkbox team-season">
            <input type="checkbox" checked={form.season} onChange={set('season')} disabled={readOnly} />
            Season
          </label>
          <div className="game-info-field team-field-nation">
            <span className="game-info-label">Nation</span>
            <NationSelect value={form.nation} onChange={(ioc) => setValue('nation', ioc)} optional disabled={readOnly} />
          </div>
        </fieldset>
      </>
    )}
  />
);

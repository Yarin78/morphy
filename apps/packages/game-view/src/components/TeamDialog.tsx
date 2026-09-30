import { NATIONS, nationInfo } from '../utils/nations';
import type { NationInfo } from '../utils/nations';
import type { TeamInfo, TeamService } from '../utils/team';
import { NationFlag } from './EloTypeIcons';
import { EntityDetailsDialog } from './EntityDetailsDialog';
import { IconSelect } from './IconSelect';

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

/** A team needn't have a nation. */
const NO_NATION: NationInfo = { ioc: '', name: 'None' };

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

function nationMatches(nation: NationInfo, query: string): boolean {
  const q = query.toLowerCase();
  return nation.name.toLowerCase().includes(q) || nation.ioc.toLowerCase().startsWith(q);
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
            <IconSelect<NationInfo>
              options={[NO_NATION, ...NATIONS]}
              value={form.nation ? (nationInfo(form.nation) ?? NO_NATION) : NO_NATION}
              onChange={(nation) => setValue('nation', nation.ioc)}
              optionKey={(nation) => nation.ioc || 'none'}
              renderOption={(nation) => (
                <>
                  {nation.ioc && <NationFlag nation={nation.ioc} />}
                  <span>{nation.name}</span>
                </>
              )}
              placeholder={<span className="icon-select-placeholder">None</span>}
              label="Nation"
              matches={nationMatches}
              disabled={readOnly}
            />
          </div>
        </fieldset>
      </>
    )}
  />
);

import { teamSubtitle } from '../utils/team';
import type { TeamInfo, TeamService } from '../utils/team';
import { EntityField } from './EntityField';

interface TeamFieldProps {
  value: TeamInfo | null;
  onChange: (team: TeamInfo | null) => void;
  service?: TeamService;
  /** What the field is, e.g. "White team". */
  label: string;
}

/** A player's team, by title, with suggestions of existing teams; see EntityField. */
export const TeamField: React.FC<TeamFieldProps> = ({ value, onChange, service, label }) => (
  <EntityField<TeamInfo>
    value={value}
    onChange={onChange}
    search={service?.search}
    create={(title) => ({ id: null, title })}
    hasDetails={hasDetails}
    subtitle={teamSubtitle}
    what="team"
    ariaLabel={label}
    placeholder="No team"
    className="game-info-field-team"
  />
);

function hasDetails(t: TeamInfo): boolean {
  return Boolean(t.number || t.season || t.year || t.nation);
}

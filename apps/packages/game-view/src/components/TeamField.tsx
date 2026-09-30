import { teamSubtitle } from '../utils/team';
import type { TeamInfo, TeamService } from '../utils/team';
import { EntityCombobox, gameCountText } from './EntityCombobox';

interface TeamFieldProps {
  value: TeamInfo | null;
  onChange: (team: TeamInfo | null) => void;
  service?: TeamService;
  /** What the field is, e.g. "White team". */
  label: string;
}

/**
 * A player's team, by title, with suggestions of existing teams to pick from while typing; empty
 * for no team. A badge marks a new team, one that saving the game will create; editing the title
 * of an existing one makes it a new team, since the existing one belongs to other games too.
 */
export const TeamField: React.FC<TeamFieldProps> = ({ value, onChange, service, label }) => {
  const handleTextChange = (title: string) => {
    if (!value || value.id != null) {
      // A team created now, without any existing one's details
      onChange(title ? { id: null, title } : null);
    } else {
      // A new team keeps the details entered for it
      onChange(title || hasDetails(value) ? { ...value, title } : null);
    }
  };
  const isNew = Boolean(service) && value !== null && value.id == null;
  return (
    <div className={`game-info-field game-info-field-team${isNew ? ' with-badge' : ''}`}>
      <EntityCombobox<TeamInfo>
        text={value?.title ?? ''}
        onTextChange={handleTextChange}
        search={service?.search}
        onChoose={onChange}
        optionKey={(t) => t.id ?? t.title}
        optionTitle={(t) => t.title}
        optionSubtitle={(t) => [teamSubtitle(t), gameCountText(t.gameCount)].filter(Boolean).join(' · ')}
        newWhat="team"
        inputProps={{ 'aria-label': label, placeholder: 'No team' }}
      />
      {isNew && <span className="entity-badge entity-badge-new entity-badge-inline">New</span>}
    </div>
  );
};

function hasDetails(t: TeamInfo): boolean {
  return Boolean(t.number || t.season || t.year || t.nation);
}

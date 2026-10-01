import { newTournament, tournamentSubtitle } from '../utils/tournament';
import type { TournamentInfo, TournamentService } from '../utils/tournament';
import { EntityCombobox, gameCountText } from './EntityCombobox';

interface TournamentFieldProps {
  value: TournamentInfo | null;
  onChange: (tournament: TournamentInfo | null) => void;
  service?: TournamentService;
}

/**
 * The tournament's name, with suggestions of existing tournaments to pick from while typing. A
 * badge marks a new tournament, one that saving the game will create; editing the name of an
 * existing one makes it a new tournament, since the existing one belongs to other games too.
 */
export const TournamentField: React.FC<TournamentFieldProps> = ({ value, onChange, service }) => {
  const handleTextChange = (title: string) => {
    if (!value || value.id != null) {
      // A tournament created now, without any existing one's details
      onChange(title ? newTournament(title) : null);
    } else {
      // A new tournament keeps the details entered for it
      onChange(title || hasDetails(value) ? { ...value, title } : null);
    }
  };

  const isNew = Boolean(service) && value !== null && value.id == null;
  return (
    <div className="game-info-field game-info-field-tournament">
      <span className="game-info-label">Name</span>
      <div className={`entity-input${isNew ? ' with-badge' : ''}`}>
        <EntityCombobox<TournamentInfo>
          text={value?.title ?? ''}
          onTextChange={handleTextChange}
          search={service?.search}
          onChoose={onChange}
          optionKey={(t) => t.id ?? t.title}
          optionTitle={(t) => t.title}
          optionSubtitle={(t) => [tournamentSubtitle(t), gameCountText(t.gameCount)].filter(Boolean).join(' · ')}
          newWhat="tournament"
          inputProps={{ 'aria-label': 'Tournament name' }}
        />
        {isNew && <span className="entity-badge entity-badge-new entity-badge-inline">New</span>}
      </div>
    </div>
  );
};

function hasDetails(t: TournamentInfo): boolean {
  return Boolean(
    t.startDate || t.endDate || t.place || t.nation || t.type || t.timeControl || t.rounds || t.category ||
      t.complete || t.teamTournament
  );
}

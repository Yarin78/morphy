import type { PlayerInfo, PlayerService } from '../utils/player';
import { EntityCombobox, gameCountText } from './EntityCombobox';

interface PlayerFieldProps {
  value: PlayerInfo;
  onChange: (player: PlayerInfo) => void;
  service?: PlayerService;
  /** What the field is, e.g. "White player". */
  label: string;
  inputRef?: React.Ref<HTMLInputElement>;
}

/**
 * A player's name, "Lastname, Firstname", with suggestions of existing players to pick from while
 * typing. A badge marks a name that isn't an existing player picked from the suggestions: saving
 * finds the player by name, or creates one.
 */
export const PlayerField: React.FC<PlayerFieldProps> = ({ value, onChange, service, label, inputRef }) => {
  const isNew = Boolean(service) && value.id == null && value.name.trim() !== '';
  return (
    <div className={`game-info-field game-info-field-player${isNew ? ' with-badge' : ''}`}>
      <EntityCombobox<PlayerInfo>
        text={value.name}
        // Any change to the name makes it a player to find or create by that name
        onTextChange={(name) => onChange({ id: null, name })}
        search={service?.search}
        onChoose={onChange}
        optionKey={(p) => p.id ?? p.name}
        optionTitle={(p) => p.name}
        optionSubtitle={(p) => gameCountText(p.gameCount) ?? ''}
        newWhat="player"
        inputProps={{ 'aria-label': label, placeholder: 'Last name, First name', ref: inputRef }}
      />
      {isNew && <span className="entity-badge entity-badge-new entity-badge-inline">New</span>}
    </div>
  );
};

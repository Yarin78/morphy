import { newTournament, tournamentSubtitle } from '../utils/tournament';
import type { TournamentInfo, TournamentService } from '../utils/tournament';
import { EntityField } from './EntityField';

interface TournamentFieldProps {
  value: TournamentInfo | null;
  onChange: (tournament: TournamentInfo | null) => void;
  service?: TournamentService;
}

/** The tournament's name, with suggestions of existing tournaments; see EntityField. */
export const TournamentField: React.FC<TournamentFieldProps> = ({ value, onChange, service }) => (
  <EntityField<TournamentInfo>
    value={value}
    onChange={onChange}
    search={service?.search}
    create={newTournament}
    hasDetails={hasDetails}
    subtitle={tournamentSubtitle}
    what="tournament"
    label="Name"
    ariaLabel="Tournament name"
    className="game-info-field-tournament"
  />
);

function hasDetails(t: TournamentInfo): boolean {
  return Boolean(
    t.startDate || t.endDate || t.place || t.nation || t.type || t.timeControl || t.rounds || t.category ||
      t.complete || t.teamTournament
  );
}

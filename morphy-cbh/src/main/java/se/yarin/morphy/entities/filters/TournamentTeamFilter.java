package se.yarin.morphy.entities.filters;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.entities.Tournament;

public class TournamentTeamFilter implements EntityFilter<Tournament>  {
    @Override
    public boolean matches(@NotNull Tournament tournament) {
        return tournament.teamTournament();
    }

    @Override
    public boolean matchesSerialized(byte[] serializedItem) {
        return (serializedItem[75] & 1) == 1;
    }
}

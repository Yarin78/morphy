package se.yarin.morphy.entities.filters;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.entities.Tournament;
import se.yarin.morphy.storage.ItemStorageFilter;

public class TournamentTeamFilter implements ItemStorageFilter<Tournament> {
    @Override
    public boolean matches(@NotNull Tournament tournament) {
        return tournament.teamTournament();
    }
}

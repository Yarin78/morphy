package se.yarin.morphy.queries;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.entities.Tournament;
import se.yarin.morphy.entities.filters.TournamentTeamFilter;

import java.util.stream.Stream;

public class QTournamentsIsTeam extends ItemQuery<Tournament> {
    private final TournamentTeamFilter filter;

    public QTournamentsIsTeam() {
        this.filter = new TournamentTeamFilter();
    }

    @Override
    public boolean matches(@NotNull DatabaseReadTransaction txn, @NotNull Tournament tournament) {
        return filter.matches(tournament);
    }

    @Override
    public int rowEstimate(@NotNull DatabaseReadTransaction txn) {
        return INFINITE;
    }

    @Override
    public @NotNull Stream<Tournament> stream(@NotNull DatabaseReadTransaction txn) {
        return txn.tournamentTransaction().stream().filter(filter::matches);
    }
}

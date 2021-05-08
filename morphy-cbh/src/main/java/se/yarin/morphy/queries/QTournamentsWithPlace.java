package se.yarin.morphy.queries;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.entities.Tournament;
import se.yarin.morphy.entities.filters.TournamentPlaceFilter;

import java.util.stream.Stream;

public class QTournamentsWithPlace extends ItemQuery<Tournament> {
    private final @NotNull TournamentPlaceFilter filter;

    public QTournamentsWithPlace(@NotNull TournamentPlaceFilter filter) {
        this.filter = filter;
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

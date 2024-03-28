package se.yarin.morphy.qqueries;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.entities.Tournament;
import se.yarin.morphy.entities.filters.TournamentYearTitleFilter;

import java.util.stream.Stream;

public class QTournamentsWithYearTitle extends ItemQuery<Tournament> {
    private final @NotNull TournamentYearTitleFilter filter;

    public QTournamentsWithYearTitle(@NotNull TournamentYearTitleFilter filter) {
        this.filter = filter;
    }

    @Override
    public boolean matches(@NotNull DatabaseReadTransaction txn, @NotNull Tournament tournament) {
        return filter.matches(tournament);
    }

    @Override
    public int rowEstimate(@NotNull DatabaseReadTransaction txn) {
        // TODO: if case sensitive, we can iterate alphabetically in the index and know if there are few matching
        return INFINITE;
    }

    @Override
    public @NotNull Stream<Tournament> stream(@NotNull DatabaseReadTransaction txn) {
        return txn.tournamentTransaction().stream().filter(filter::matches);
    }
}

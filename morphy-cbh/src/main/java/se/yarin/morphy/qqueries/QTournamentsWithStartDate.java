package se.yarin.morphy.qqueries;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.entities.Tournament;
import se.yarin.morphy.entities.filters.TournamentStartDateFilter;

import java.util.stream.Stream;

public class QTournamentsWithStartDate extends ItemQuery<Tournament> {
    private final @NotNull TournamentStartDateFilter filter;

    public QTournamentsWithStartDate(@NotNull String dateRange) {
        this(new TournamentStartDateFilter(dateRange));
    }

    public QTournamentsWithStartDate(@NotNull TournamentStartDateFilter filter) {
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

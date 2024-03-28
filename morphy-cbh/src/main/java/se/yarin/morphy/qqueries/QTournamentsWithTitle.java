package se.yarin.morphy.qqueries;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.entities.Tournament;
import se.yarin.morphy.entities.filters.TournamentTitleFilter;

import java.util.stream.Stream;

public class QTournamentsWithTitle extends ItemQuery<Tournament> {
    private final @NotNull TournamentTitleFilter filter;

    public QTournamentsWithTitle(@NotNull String title, boolean caseSensitive, boolean exactMatch) {
        this(new TournamentTitleFilter(title, caseSensitive, exactMatch));
    }

    public QTournamentsWithTitle(@NotNull TournamentTitleFilter filter) {
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
        return txn.tournamentTransaction().stream().filter(tournament -> matches(txn, tournament));
    }
}

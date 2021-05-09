package se.yarin.morphy.queries;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.entities.Tournament;
import se.yarin.morphy.entities.filters.RawTournamentFilter;

import java.util.stream.Stream;

public class QTournamentsWithRaw extends ItemQuery<Tournament> {
    private final @NotNull RawTournamentFilter filter;

    public QTournamentsWithRaw(@NotNull RawTournamentFilter filter) {
        this.filter = filter;
    }

    @Override
    public boolean matches(@NotNull DatabaseReadTransaction txn, @NotNull Tournament tournament) {
        // This actually matches everything
        // TODO: Force match serialized?
        return filter.matches(tournament);
    }

    @Override
    public int rowEstimate(@NotNull DatabaseReadTransaction txn) {
        return INFINITE;
    }

    @Override
    public @NotNull Stream<Tournament> stream(@NotNull DatabaseReadTransaction txn) {
        // TODO: Need low level filtering
        return txn.tournamentTransaction().stream().filter(filter::matches);
    }
}

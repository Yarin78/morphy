package se.yarin.morphy.queries;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.entities.Tournament;

import java.util.stream.Stream;

public class QTournamentsAll extends ItemQuery<Tournament> {
    @Override
    public boolean matches(@NotNull DatabaseReadTransaction txn, @NotNull Tournament item) {
        return true;
    }

    @Override
    public int rowEstimate(@NotNull DatabaseReadTransaction txn) {
        return INFINITE;
    }

    @Override
    public @NotNull Stream<Tournament> stream(@NotNull DatabaseReadTransaction txn) {
        return txn.tournamentTransaction().stream();
    }
}

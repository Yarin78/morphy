package se.yarin.morphy.queries;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.entities.Player;

import java.util.stream.Stream;

public class QPlayersAll extends ItemQuery<Player> {
    @Override
    public boolean matches(@NotNull DatabaseReadTransaction txn, @NotNull Player item) {
        return true;
    }

    @Override
    public int rowEstimate(@NotNull DatabaseReadTransaction txn) {
        return INFINITE;
    }

    @Override
    public @NotNull Stream<Player> stream(@NotNull DatabaseReadTransaction txn) {
        return txn.playerTransaction().stream();
    }
}

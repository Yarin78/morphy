package se.yarin.morphy.qqueries;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.entities.Player;
import se.yarin.morphy.entities.filters.PlayerNameFilter;

import java.util.stream.Stream;

public class QPlayersWithName extends ItemQuery<Player> {
    private final @NotNull PlayerNameFilter filter;

    public QPlayersWithName(@NotNull String name, boolean caseSensitive, boolean exactMatch) {
        this(new PlayerNameFilter(name, caseSensitive, exactMatch));
    }

    public QPlayersWithName(@NotNull String lastName, @NotNull String firstName, boolean caseSensitive, boolean exactMatch) {
        this(new PlayerNameFilter(lastName, firstName, caseSensitive, exactMatch));
    }

    public QPlayersWithName(@NotNull PlayerNameFilter filter) {
        this.filter = filter;
    }

    @Override
    public boolean matches(@NotNull DatabaseReadTransaction txn, @NotNull Player player) {
        return filter.matches(player);
    }

    @Override
    public int rowEstimate(@NotNull DatabaseReadTransaction txn) {
        // TODO: if case sensitive, we can iterate alphabetically in the index and know if there are few matching
        return INFINITE;
    }

    @Override
    public @NotNull Stream<Player> stream(@NotNull DatabaseReadTransaction txn) {
        // TODO: Serialization stream
        return txn.playerTransaction().stream().filter(player -> matches(txn, player));
    }
}

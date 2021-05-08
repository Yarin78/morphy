package se.yarin.morphy.queries;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.Game;
import se.yarin.morphy.entities.Player;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class QPlayersWithId extends ItemQuery<Player> {
    private final @NotNull Set<Player> players;
    private final @NotNull Set<Integer> playerIds;

    public QPlayersWithId(@NotNull Collection<Player> players) {
        this.players = new HashSet<>(players);
        this.playerIds = players.stream().map(Player::id).collect(Collectors.toCollection(HashSet::new));
    }

    @Override
    public boolean matches(@NotNull DatabaseReadTransaction txn, @NotNull Player player) {
        return playerIds.contains(player.id());
    }

    @Override
    public int rowEstimate(@NotNull DatabaseReadTransaction txn) {
        return players.size();
    }

    @Override
    public @NotNull Stream<Player> stream(@NotNull DatabaseReadTransaction txn) {
        return players.stream();
    }
}

package se.yarin.morphy.qqueries;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.Game;
import se.yarin.morphy.entities.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public class QPlayersByGames extends ItemQuery<Player> {
    private final @NotNull ItemQuery<Game> gameQuery;
    private @Nullable List<Player> aggregatedPlayers;
    private @Nullable Set<Integer> aggregatedPlayerIds;

    public QPlayersByGames(@NotNull ItemQuery<Game> gameQuery) {
        this.gameQuery = gameQuery;
    }

    public void evaluateSubQuery(@NotNull DatabaseReadTransaction txn) {
        if (aggregatedPlayers == null) {
            ArrayList<Player> players = new ArrayList<>();
            HashSet<Integer> playerIds = new HashSet<>();
            gameQuery.stream(txn).forEach(game -> {
                if (!game.guidingText()) {
                    if (!playerIds.contains(game.whitePlayerId())) {
                        playerIds.add(game.whitePlayerId());
                        players.add(game.white());
                    }
                    if (!playerIds.contains(game.blackPlayerId())) {
                        playerIds.add(game.blackPlayerId());
                        players.add(game.black());
                    }
                }
            });
            aggregatedPlayers = players;
            aggregatedPlayerIds = playerIds;
        }
    }

    @Override
    public boolean matches(@NotNull DatabaseReadTransaction txn, @NotNull Player player) {
        evaluateSubQuery(txn);
        assert aggregatedPlayerIds != null;
        return aggregatedPlayerIds.contains(player.id());
    }

    @Override
    public int rowEstimate(@NotNull DatabaseReadTransaction txn) {
        if (aggregatedPlayers == null) {
            return txn.playerTransaction().index().count();
        }
        return aggregatedPlayers.size();
    }

    @Override
    public @NotNull Stream<Player> stream(@NotNull DatabaseReadTransaction txn) {
        evaluateSubQuery(txn);
        assert aggregatedPlayers != null;
        return aggregatedPlayers.stream();
    }
}

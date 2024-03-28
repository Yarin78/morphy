package se.yarin.morphy.qqueries;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.Game;
import se.yarin.morphy.entities.Entity;
import se.yarin.morphy.entities.Player;
import se.yarin.morphy.games.filters.GameFilter;
import se.yarin.morphy.games.filters.PlayerFilter;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class QGamesByPlayers extends ItemQuery<Game> {
    private final @NotNull ItemQuery<Player> playerQuery;
    private final PlayerFilter.PlayerColor color;
    private final PlayerFilter.PlayerResult result;

    private @Nullable List<Player> playerResult;
    private @Nullable PlayerFilter playerFilter;


    public QGamesByPlayers(@NotNull ItemQuery<Player> playerQuery) {
        this(playerQuery, PlayerFilter.PlayerColor.ANY, PlayerFilter.PlayerResult.ANY);
    }

    public QGamesByPlayers(@NotNull ItemQuery<Player> playerQuery, @NotNull PlayerFilter.PlayerColor color, @NotNull PlayerFilter.PlayerResult result) {
        this.playerQuery = playerQuery;
        this.color = color;
        this.result = result;
    }

    @Override
    public boolean matches(@NotNull DatabaseReadTransaction txn, @NotNull Game game) {
        evaluateSubQuery(txn);
        assert playerFilter != null;
        return playerFilter.matches(game.header());
    }

    public int rowEstimate(@NotNull DatabaseReadTransaction txn) {
        if (playerResult == null) {
            return INFINITE;
        }
        return playerResult.stream().map(Entity::count).reduce(0, Integer::sum);
    }

    public void evaluateSubQuery(@NotNull DatabaseReadTransaction txn) {
        if (playerResult == null) {
            playerResult = playerQuery.stream(txn).collect(Collectors.toList());
            playerFilter = new PlayerFilter(playerResult, color, result);
        }
    }

    @Override
    public @NotNull Stream<Game> stream(@NotNull DatabaseReadTransaction txn) {
        evaluateSubQuery(txn);
        assert playerFilter != null;
        return txn.stream(GameFilter.of(playerFilter, null));
    }
}

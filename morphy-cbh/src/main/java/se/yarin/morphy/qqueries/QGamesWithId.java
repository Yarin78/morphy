package se.yarin.morphy.qqueries;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.Game;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

public class QGamesWithId extends ItemQuery<Game> {
    private final @NotNull Set<Integer> gameIds;

    public QGamesWithId(int gameId) {
        this.gameIds = Set.of(gameId);
    }

    public QGamesWithId(@NotNull Collection<Integer> gameIds) {
        this.gameIds = new HashSet<>(gameIds);
    }

    @Override
    public boolean matches(@NotNull DatabaseReadTransaction txn, @NotNull Game game) {
        return gameIds.contains(game.id());
    }

    @Override
    public int rowEstimate(@NotNull DatabaseReadTransaction txn) {
        return gameIds.size();
    }

    @Override
    public @NotNull Stream<Game> stream(@NotNull DatabaseReadTransaction txn) {
        return gameIds.stream().map(txn::getGame);
    }
}

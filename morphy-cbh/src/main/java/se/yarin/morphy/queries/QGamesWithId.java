package se.yarin.morphy.queries;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.Game;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class QGamesWithId extends ItemQuery<Game> {
    private final @NotNull Set<Game> games;
    private final @NotNull Set<Integer> gameIds;

    public QGamesWithId(@NotNull Collection<Game> games) {
        this.games = new HashSet<>(games);
        this.gameIds = games.stream().map(Game::id).collect(Collectors.toCollection(HashSet::new));
    }

    @Override
    public boolean matches(@NotNull DatabaseReadTransaction txn, @NotNull Game game) {
        return gameIds.contains(game.id());
    }

    @Override
    public int rowEstimate(@NotNull DatabaseReadTransaction txn) {
        return games.size();
    }

    @Override
    public @NotNull Stream<Game> stream(@NotNull DatabaseReadTransaction txn) {
        return games.stream();
    }
}

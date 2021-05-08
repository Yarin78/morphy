package se.yarin.morphy.queries;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.Game;
import se.yarin.morphy.games.filters.GameFilter;
import se.yarin.morphy.games.filters.GameStorageFilter;

import java.util.stream.Stream;

public class QGamesIsGame extends ItemQuery<Game> {
    private final GameStorageFilter filter = new GameStorageFilter();

    @Override
    public boolean matches(@NotNull DatabaseReadTransaction txn, @NotNull Game game) {
        return !game.guidingText();
    }

    @Override
    public int rowEstimate(@NotNull DatabaseReadTransaction txn) {
        return INFINITE;
    }

    @Override
    public @NotNull Stream<Game> stream(@NotNull DatabaseReadTransaction txn) {
        return txn.stream(GameFilter.of(filter, null));
    }
}

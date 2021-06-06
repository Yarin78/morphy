package se.yarin.morphy.queries;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.Database;
import se.yarin.morphy.games.filters.GameFilter;

import java.util.List;

public class GameQuery {
    private final @NotNull Database database;
    private final @NotNull List<GameFilter> gameFilters;

    public GameQuery(@NotNull Database database, @NotNull List<GameFilter> gameFilters) {
        this.database = database;
        this.gameFilters = List.copyOf(gameFilters);
    }

    public @NotNull Database database() {
        return database;
    }

    public @NotNull List<GameFilter> gameFilters() {
        return gameFilters;
    }
}

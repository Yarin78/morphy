package se.yarin.morphy.queries;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.Database;
import se.yarin.morphy.Game;
import se.yarin.morphy.games.filters.GameFilter;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class GameQuery {
    private final @NotNull Database database;
    private final @NotNull List<GameFilter> gameFilters;
    private final @NotNull List<GameEntityJoin<?>> entityJoins;

    private final @Nullable QuerySortOrder<Game> sortOrder;
    private final int limit;  // 0 = all

    public GameQuery(@NotNull Database database,
                     @Nullable List<GameFilter> gameFilters) {
        this(database, gameFilters, null);
    }

    public GameQuery(@NotNull Database database,
                     @Nullable List<GameFilter> gameFilters,
                     @Nullable List<GameEntityJoin<?>> entityJoins) {
        this(database, gameFilters, entityJoins, null, 0);
    }

    public GameQuery(@NotNull Database database,
                     @Nullable List<GameFilter> gameFilters,
                     @Nullable List<GameEntityJoin<?>> entityJoins,
                     @Nullable QuerySortOrder<Game> sortOrder,
                     int limit) {
        this.database = database;
        this.gameFilters = gameFilters == null ? List.of() : List.copyOf(gameFilters);
        this.entityJoins = entityJoins == null ? List.of() : List.copyOf(entityJoins);
        this.sortOrder = sortOrder == null ? QuerySortOrder.none() : sortOrder;
        this.limit = limit;
    }

    public @NotNull Database database() {
        return database;
    }

    public @NotNull List<GameFilter> gameFilters() {
        return gameFilters;
    }

    public @NotNull List<GameEntityJoin<?>> entityJoins() {
        return entityJoins;
    }

    public @NotNull QuerySortOrder<Game> sortOrder() {
        return sortOrder;
    }

    public int limit() {
        return limit;
    }

    public List<GameEntityJoin<?>> entityJoins(boolean filtersOnly) {
        return entityJoins.stream()
                .filter(entityJoin -> !entityJoin.entityQuery().filters().isEmpty() || !filtersOnly)
                .collect(Collectors.toCollection(ArrayList::new));
    }
}

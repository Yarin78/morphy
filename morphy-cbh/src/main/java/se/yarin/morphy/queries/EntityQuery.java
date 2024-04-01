package se.yarin.morphy.queries;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.Database;
import se.yarin.morphy.IdObject;
import se.yarin.morphy.entities.filters.EntityFilter;

import java.util.List;

public abstract class EntityQuery<T extends IdObject> {
    private final @NotNull Database database;
    private final @NotNull List<EntityFilter<T>> filters;

    // These are either both set or both null
    private final @Nullable GameQuery gameQuery;

    private final @NotNull QuerySortOrder<T> sortOrder;
    private final int limit;  // 0 = all

    public @NotNull Database database() {
        return database;
    }

    public @NotNull List<EntityFilter<T>> filters() {
        return filters;
    }

    public @Nullable GameQuery gameQuery() {
        return gameQuery;
    }

    public @NotNull QuerySortOrder<T> sortOrder() {
        return sortOrder;
    }

    public int limit() {
        return limit;
    }

    public EntityQuery(@NotNull Database database,
                       @Nullable List<EntityFilter<T>> filters,
                       @Nullable GameQuery gameQuery,
                       @Nullable QuerySortOrder<T> sortOrder,
                       int limit) {
        this.database = database;
        this.filters = filters == null ? List.of() : List.copyOf(filters);
        this.gameQuery = gameQuery;
        this.sortOrder = sortOrder == null ? QuerySortOrder.none() : sortOrder;
        this.limit = limit;
    }
}

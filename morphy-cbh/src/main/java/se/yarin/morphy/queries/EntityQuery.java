package se.yarin.morphy.queries;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.Database;
import se.yarin.morphy.IdObject;
import se.yarin.morphy.entities.Entity;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.entities.filters.EntityFilter;
import se.yarin.morphy.entities.filters.ManualFilter;

import java.util.List;

public class EntityQuery<T extends IdObject> {
    private final @NotNull Database database;
    private final @NotNull List<EntityFilter<T>> filters;

    private final @NotNull EntityType entityType;

    private final @Nullable GameQuery gameQuery;
    private final @Nullable GameQueryJoinCondition joinCondition;

    private final @NotNull QuerySortOrder<T> sortOrder;
    private final int limit;  // 0 = all

    public @NotNull Database database() {
        return database;
    }

    public @NotNull EntityType entityType() {
        return entityType;
    }

    public @NotNull List<EntityFilter<T>> filters() {
        return filters;
    }

    public @Nullable GameQuery gameQuery() {
        return gameQuery;
    }

    public @Nullable GameQueryJoinCondition joinCondition() {
        return joinCondition;
    }

    public @NotNull QuerySortOrder<T> sortOrder() {
        return sortOrder;
    }

    public int limit() {
        return limit;
    }

    public static <T extends Entity & Comparable<T>> EntityQuery<T> manual(@NotNull Database database, @NotNull EntityType entityType, @NotNull List<T> entities) {
        return new EntityQuery<T>(database, entityType, List.of(new ManualFilter<>(entities, entityType)));
    }

    public EntityQuery(@NotNull Database database, @NotNull EntityType entityType, @Nullable List<EntityFilter<T>> filters) {
        this(database, entityType, filters, null, null, null, 0);
    }

    public EntityQuery(@NotNull Database database, @NotNull EntityType entityType, @Nullable List<EntityFilter<T>> filters, @Nullable GameQuery gameQuery,
                       @Nullable GameQueryJoinCondition joinCondition) {
        this(database, entityType, filters, gameQuery, joinCondition, null, 0);
    }

    public EntityQuery(@NotNull Database database, @NotNull EntityType entityType, @Nullable List<EntityFilter<T>> filters, @Nullable QuerySortOrder<T> sortOrder,
                       int limit) {
        this(database, entityType, filters, null, null, sortOrder, limit);
    }

    public EntityQuery(@NotNull Database database,
                       @NotNull EntityType entityType,
                       @Nullable List<EntityFilter<T>> filters,
                       @Nullable GameQuery gameQuery,
                       @Nullable GameQueryJoinCondition joinCondition,
                       @Nullable QuerySortOrder<T> sortOrder,
                       int limit) {
        this.database = database;
        this.entityType = entityType;
        this.filters = filters == null ? List.of() : List.copyOf(filters);
        this.gameQuery = gameQuery;
        this.joinCondition = joinCondition;
        this.sortOrder = sortOrder == null ? QuerySortOrder.none() : sortOrder;
        this.limit = limit;
    }
}

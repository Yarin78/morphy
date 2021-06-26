package se.yarin.morphy.queries;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.Database;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.entities.filters.EntityFilter;

import java.util.List;

public class EntityQuery<T> {
    private final @NotNull Database database;
    private final @NotNull List<? extends EntityFilter<T>> entityFilters;
    private final @Nullable GameQuery gameQuery;
    private final @NotNull EntityType entityType;

    private static <T> @NotNull EntityType resolveEntityType(List<? extends EntityFilter<T>> entityFilters) {
        if (entityFilters.size() == 0) {
            throw new IllegalArgumentException("At least one filter must be specified to deduce entity type");
        }
        return entityFilters.get(0).entityType();
    }

    public EntityQuery(@NotNull Database database,
                       @NotNull List<? extends EntityFilter<T>> entityFilters) {
        this(database, entityFilters, null, resolveEntityType(entityFilters));
    }

    public EntityQuery(@NotNull Database database,
                       @NotNull List<? extends EntityFilter<T>> entityFilters,
                       @Nullable GameQuery gameQuery) {
        this(database, entityFilters, gameQuery, resolveEntityType(entityFilters));
    }

    public EntityQuery(@NotNull Database database,
                       @Nullable List<? extends EntityFilter<T>> entityFilters,
                       @Nullable GameQuery gameQuery,
                       @NotNull EntityType entityType) {
        this.database = database;
        this.entityFilters = entityFilters == null ? List.of() : List.copyOf(entityFilters);
        this.gameQuery = gameQuery;
        this.entityType = entityType;

        if (!this.entityFilters.stream().allMatch(filter -> filter.entityType().equals(this.entityType))) {
            throw new IllegalArgumentException("Can't mix entity filters of different types");
        }
    }

    public @NotNull Database database() {
        return database;
    }

    public @NotNull List<? extends EntityFilter<T>> entityFilters() {
        return entityFilters;
    }

    public @Nullable GameQuery gameQuery() {
        return gameQuery;
    }

    public EntityType entityType() {
        return this.entityType;
    }
}

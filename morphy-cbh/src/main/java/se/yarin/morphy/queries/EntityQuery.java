package se.yarin.morphy.queries;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.Database;
import se.yarin.morphy.entities.filters.EntityFilter;

import java.util.List;

public class EntityQuery<T> {
    private final @NotNull Database database;
    private final @NotNull List<EntityFilter<T>> entityFilters;

    public EntityQuery(@NotNull Database database, @NotNull List<EntityFilter<T>> entityFilters) {
        this.database = database;
        this.entityFilters = List.copyOf(entityFilters);
    }
}

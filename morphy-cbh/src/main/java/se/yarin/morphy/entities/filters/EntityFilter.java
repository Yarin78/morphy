package se.yarin.morphy.entities.filters;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.queries.QueryPlanner;

public interface EntityFilter<T> {
    boolean matches(@NotNull T item);

    EntityType entityType();

    default boolean matchesSerialized(byte[] serializedItem) {
        return true;
    }

    default double expectedMatch(@NotNull QueryPlanner planner) { return 1.0; }
}

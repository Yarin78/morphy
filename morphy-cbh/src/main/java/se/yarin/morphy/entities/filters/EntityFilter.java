package se.yarin.morphy.entities.filters;

import org.jetbrains.annotations.NotNull;

public interface EntityFilter<T> {
    boolean matches(@NotNull T item);

    default boolean matchesSerialized(byte[] serializedItem) {
        return true;
    }
}

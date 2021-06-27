package se.yarin.morphy.entities.filters;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.entities.EntityType;

public class AllFilter<T> implements EntityFilter<T> {

    private final @NotNull EntityType entityType;

    public AllFilter(@NotNull EntityType entityType) {
        this.entityType = entityType;
    }

    @Override
    public boolean matches(@NotNull T item) {
        return true;
    }

    @Override
    public EntityType entityType() {
        return this.entityType;
    }

    @Override
    public String toString() {
        return "All";
    }
}

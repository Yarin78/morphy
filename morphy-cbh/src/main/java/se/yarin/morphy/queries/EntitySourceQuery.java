package se.yarin.morphy.queries;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.IdObject;
import se.yarin.morphy.queries.operations.QueryOperator;

class EntitySourceQuery<T extends IdObject> {
    private final @NotNull QueryOperator<T> entityOperator;

    public @NotNull QueryOperator<T> entityOperator() {
        return entityOperator;
    }

    public EntitySourceQuery(@NotNull QueryOperator<T> entityOperator) {
        this.entityOperator = entityOperator;
    }
}

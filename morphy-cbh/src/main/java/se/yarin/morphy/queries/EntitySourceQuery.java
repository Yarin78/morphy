package se.yarin.morphy.queries;

import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.queries.operations.QueryOperator;

class EntitySourceQuery<T> {
    // Exactly one of these two operators are set
    private final @Nullable QueryOperator<T> entityOperator;
    private final @Nullable QueryOperator<Integer> entityIdOperator;

    public @Nullable QueryOperator<T> entityOperator() {
        return entityOperator;
    }

    public @Nullable QueryOperator<Integer> entityIdOperator() {
        return entityIdOperator;
    }

    private EntitySourceQuery(@Nullable QueryOperator<T> entityOperator, @Nullable QueryOperator<Integer> entityIdOperator) {
        this.entityOperator = entityOperator;
        this.entityIdOperator = entityIdOperator;
    }

    public static <T> EntitySourceQuery<T> fromEntityQuery(@Nullable QueryOperator<T> entityOperator) {
        return new EntitySourceQuery<>(entityOperator, null);
    }

    public static <T> EntitySourceQuery<T> fromIdQuery(@Nullable QueryOperator<Integer> entityIdOperator) {
        return new EntitySourceQuery<>(null, entityIdOperator);
    }
}

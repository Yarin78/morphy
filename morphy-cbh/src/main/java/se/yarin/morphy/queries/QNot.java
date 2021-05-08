package se.yarin.morphy.queries;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.exceptions.MorphyNotSupportedException;

import java.util.stream.Stream;

public class QNot<T> extends ItemQuery<T> {
    private final @NotNull ItemQuery<T> negatedQuery;

    public QNot(@NotNull ItemQuery<T> negatedQuery) {
        this.negatedQuery = negatedQuery;
    }

    @Override
    public boolean matches(@NotNull DatabaseReadTransaction txn, @NotNull T item) {
        return !negatedQuery.matches(txn, item);
    }

    @Override
    public int rowEstimate(@NotNull DatabaseReadTransaction txn) {
        return INFINITE;
    }

    @Override
    public @NotNull Stream<T> stream(@NotNull DatabaseReadTransaction txn) {
        // TODO: Support this
        throw new MorphyNotSupportedException("Can't stream from a NOT query");
    }
}

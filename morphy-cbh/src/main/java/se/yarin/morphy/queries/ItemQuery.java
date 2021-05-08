package se.yarin.morphy.queries;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.DatabaseReadTransaction;

import java.util.stream.Stream;

public abstract class ItemQuery<T> {

    static final int INFINITE = 10000000;

    public abstract boolean matches(@NotNull DatabaseReadTransaction txn, @NotNull T item);

    /**
     * Gets a rough estimates on the number of items that will be returned by this query
     */
    public abstract int rowEstimate(@NotNull DatabaseReadTransaction txn);

    @NotNull public abstract Stream<T> stream(@NotNull DatabaseReadTransaction txn);
}

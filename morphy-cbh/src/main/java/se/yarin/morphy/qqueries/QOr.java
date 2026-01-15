package se.yarin.morphy.qqueries;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.DatabaseReadTransaction;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class QOr<T> extends ItemQuery<T> {
    // A list of queries that should be Or:ed together
    // Will never contain another QOr query
    private final @NotNull List<ItemQuery<T>> orQueries;

    public QOr(@NotNull ItemQuery<T> left, @NotNull ItemQuery<T> right) {
        this(Arrays.asList(left, right));
    }

    public QOr(@NotNull List<ItemQuery<T>> subQueries) {
        this.orQueries = new ArrayList<>();
        for (ItemQuery<T> subQuery : subQueries) {
            if (subQuery instanceof QOr<T> qor) {
                this.orQueries.addAll(qor.orQueries);
            } else {
                this.orQueries.add(subQuery);
            }
        }
    }

    @Override
    public boolean matches(@NotNull DatabaseReadTransaction txn, @NotNull T item) {
        return orQueries.stream().anyMatch(query -> query.matches(txn, item));
    }

    @Override
    public int rowEstimate(@NotNull DatabaseReadTransaction txn) {
        return Collections.max(orQueries.stream().map(query -> query.rowEstimate(txn)).collect(Collectors.toList()));
    }

    @Override
    public @NotNull Stream<T> stream(@NotNull DatabaseReadTransaction txn) {
        HashSet<T> items = new HashSet<>();
        for (ItemQuery<T> orQuery : orQueries) {
            orQuery.stream(txn).forEach(items::add);
        }
        return items.stream();
    }
}

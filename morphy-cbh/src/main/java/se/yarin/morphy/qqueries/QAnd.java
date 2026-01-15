package se.yarin.morphy.qqueries;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.DatabaseReadTransaction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class QAnd<T> extends ItemQuery<T> {
    // A list of queries that should be And:ed together
    // Will never contain another QAnd query
    private final @NotNull List<ItemQuery<T>> andQueries;

    public QAnd(@NotNull ItemQuery<T> left, @NotNull ItemQuery<T> right) {
        this(Arrays.asList(left, right));
    }

    public QAnd(@NotNull List<ItemQuery<T>> subQueries) {
        if (subQueries.size() == 0) {
            throw new IllegalArgumentException("At least one query must be passed to QAnd");
        }
        this.andQueries = new ArrayList<>();
        for (ItemQuery<T> subQuery : subQueries) {
            if (subQuery instanceof QAnd<T> qand) {
                this.andQueries.addAll(qand.andQueries);
            } else {
                this.andQueries.add(subQuery);
            }
        }
    }

    @Override
    public boolean matches(@NotNull DatabaseReadTransaction txn, @NotNull T item) {
        return andQueries.stream().allMatch(query -> query.matches(txn, item));
    }

    @Override
    public int rowEstimate(@NotNull DatabaseReadTransaction txn) {
        return Collections.min(andQueries.stream().map(query -> query.rowEstimate(txn)).collect(Collectors.toList()));
    }

    @Override
    public @NotNull Stream<T> stream(@NotNull DatabaseReadTransaction txn) {
        Stream<T> stream = null;
        for (ItemQuery<T> andQuery : andQueries) {
            stream = stream == null ? andQuery.stream(txn) : stream.filter(item -> andQuery.matches(txn, item));
        }
        assert stream != null;
        return stream;
    }
}

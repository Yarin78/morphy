package se.yarin.morphy.queries.operations;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.IdObject;
import se.yarin.morphy.entities.Tournament;
import se.yarin.morphy.metrics.MetricsProvider;
import se.yarin.morphy.queries.QueryContext;
import se.yarin.morphy.queries.QuerySortOrder;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Manual<T extends IdObject> extends QueryOperator<T> {
    private final @NotNull List<QueryData<T>> data;

    public Manual(@NotNull QueryContext queryContext, @NotNull Set<Integer> ids) {
        super(queryContext, false);
        this.data = ids.stream().sorted().map(QueryData<T>::new).collect(Collectors.toList());
    }

    @Override
    protected Stream<QueryData<T>> operatorStream() {
        return data.stream();
    }

    @Override
    public List<QueryOperator<?>> sources() {
        return List.of();
    }

    public @NotNull QuerySortOrder<T> sortOrder() {
        return QuerySortOrder.byId();
    }

    public boolean mayContainDuplicates() {
        return false;
    }

    public boolean singleItem() {
        return data.size() <= 1;
    }

    @Override
    protected void estimateOperatorCost(ImmutableOperatorCost.@NotNull Builder operatorCost) {
        operatorCost
            .estimateRows(data.size())
            .estimatePageReads(0)
            .estimateDeserializations(0);
    }

    @Override
    public String toString() {
        String commaList = data.stream().map(data -> Integer.toString(data.id())).collect(Collectors.joining(", "));
        return "Manual(ids: [" + commaList + "])";
    }
}

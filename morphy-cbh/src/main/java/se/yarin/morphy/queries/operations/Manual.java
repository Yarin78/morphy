package se.yarin.morphy.queries.operations;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.IdObject;
import se.yarin.morphy.metrics.MetricsProvider;
import se.yarin.morphy.queries.QueryContext;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Manual<T extends IdObject> extends QueryOperator<T> {
    private final @NotNull List<QueryData<T>> data;

    public Manual(@NotNull QueryContext queryContext, @NotNull List<Integer> ids) {
        super(queryContext, false);
        this.data = ids.stream().map(QueryData<T>::new).collect(Collectors.toList());
    }

    @Override
    protected List<MetricsProvider> metricProviders() {
        return List.of();
    }

    @Override
    protected Stream<QueryData<T>> operatorStream() {
        return data.stream();
    }

    @Override
    protected void estimateOperatorCost(ImmutableOperatorCost.@NotNull Builder operatorCost) {
        operatorCost
            .estimateRows(data.size())
            .estimatePageReads(0)
            .estimateDeserializations(0);
    }

    @Override
    public List<QueryOperator<?>> sources() {
        return List.of();
    }

    @Override
    public String toString() {
        String commaList = data.stream().map(data -> Integer.toString(data.id())).collect(Collectors.joining(", "));
        return "Manual(ids: [" + commaList + "])";
    }
}

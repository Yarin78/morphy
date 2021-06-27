package se.yarin.morphy.queries.operations;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.IdObject;
import se.yarin.morphy.metrics.MetricsProvider;
import se.yarin.morphy.queries.QueryContext;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public class Sort<T extends IdObject> extends QueryOperator<T> {
    private final @NotNull QueryOperator<T> source;

    public Sort(@NotNull QueryContext queryContext, @NotNull QueryOperator<T> source) {
        super(queryContext, source.hasFullData());
        this.source = source;
    }

    @Override
    public List<QueryOperator<?>> sources() {
        return List.of(source);
    }

    @Override
    public Stream<QueryData<T>> operatorStream() {
        return source.stream().sorted(Comparator.comparingInt(QueryData::id));
    }

    @Override
    public void estimateOperatorCost(@NotNull ImmutableOperatorCost.Builder operatorCost) {
        operatorCost
                .estimateRows(source.getOperatorCost().estimateRows())
                .estimateDeserializations(0)
                .estimatePageReads(0);
    }

    @Override
    public String toString() {
        return "Sort()";
    }

    @Override
    protected List<MetricsProvider> metricProviders() {
        return List.of();
    }
}

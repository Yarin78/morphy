package se.yarin.morphy.queries.operations;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.metrics.MetricsProvider;
import se.yarin.morphy.queries.QueryContext;

import java.util.List;
import java.util.stream.Stream;

public class SortId extends QueryOperator<Integer> {
    private final @NotNull QueryOperator<Integer> source;

    public SortId(@NotNull QueryContext queryContext, @NotNull QueryOperator<Integer> source) {
        super(queryContext);
        this.source = source;
    }

    @Override
    public List<QueryOperator<?>> sources() {
        return List.of(source);
    }

    @Override
    public Stream<Integer> operatorStream() {
        return source.stream().sorted();
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

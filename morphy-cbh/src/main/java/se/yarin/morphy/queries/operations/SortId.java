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
    public OperatorCost estimateCost() {
        return ImmutableOperatorCost.builder()
                .rows(source.estimateCost().rows())
                .numDeserializations(0)
                .pageReads(0)
                .build();
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

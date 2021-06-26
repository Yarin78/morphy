package se.yarin.morphy.queries.operations;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.metrics.MetricsProvider;
import se.yarin.morphy.queries.QueryContext;

import java.util.List;
import java.util.stream.Stream;

public class IntManual extends QueryOperator<Integer> {
    private @NotNull List<Integer> integers;

    public IntManual(@NotNull QueryContext queryContext, @NotNull List<Integer> integers) {
        super(queryContext);
        this.integers = List.copyOf(integers);
    }

    @Override
    protected List<MetricsProvider> metricProviders() {
        return List.of();
    }

    @Override
    protected Stream<Integer> operatorStream() {
        return integers.stream();
    }

    @Override
    protected void estimateOperatorCost(ImmutableOperatorCost.@NotNull Builder operatorCost) {
        operatorCost
            .estimateRows(integers.size())
            .estimatePageReads(0)
            .estimateDeserializations(0);
    }

    @Override
    public List<QueryOperator<?>> sources() {
        return List.of();
    }
}

package se.yarin.morphy.queries.operations;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.entities.Entity;
import se.yarin.morphy.metrics.MetricsProvider;
import se.yarin.morphy.queries.QueryContext;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public class SortEntity<T extends Entity & Comparable<T>> extends QueryOperator<T> {
    private final @NotNull QueryOperator<T> source;

    public SortEntity(@NotNull QueryContext queryContext, @NotNull QueryOperator<T> source) {
        super(queryContext);
        this.source = source;
    }

    @Override
    public List<QueryOperator<?>> sources() {
        return List.of(source);
    }

    @Override
    public Stream<T> operatorStream() {
        return source.stream().sorted(Comparator.comparingInt(Entity::id));
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

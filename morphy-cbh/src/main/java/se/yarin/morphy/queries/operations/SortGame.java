package se.yarin.morphy.queries.operations;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.Game;
import se.yarin.morphy.metrics.MetricsProvider;
import se.yarin.morphy.queries.QueryContext;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public class SortGame extends QueryOperator<Game> {
    private final @NotNull QueryOperator<Game> source;

    public SortGame(@NotNull QueryContext queryContext, @NotNull QueryOperator<Game> source) {
        super(queryContext);
        this.source = source;
    }

    @Override
    public List<QueryOperator<?>> sources() {
        return List.of(source);
    }

    @Override
    public Stream<Game> operatorStream() {
        return source.stream().sorted(Comparator.comparingInt(Game::id));
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

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
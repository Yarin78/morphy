package se.yarin.morphy.queries.operations;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.Game;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.games.filters.GameFilter;
import se.yarin.morphy.metrics.FileMetrics;
import se.yarin.morphy.metrics.ItemMetrics;
import se.yarin.morphy.metrics.MetricsProvider;
import se.yarin.morphy.metrics.MetricsRepository;
import se.yarin.morphy.queries.QueryContext;

import java.util.List;
import java.util.stream.Stream;

public class GameLookup extends QueryOperator<Game> {
    private final @Nullable GameFilter gameFilter;
    private final @NotNull QueryOperator<Integer> source;

    public GameLookup(@NotNull QueryContext queryContext, @NotNull QueryOperator<Integer> source, @Nullable GameFilter gameFilter) {
        super(queryContext);
        this.source = source;
        this.gameFilter = gameFilter;
    }

    @Override
    public List<QueryOperator<?>> sources() {
        return List.of(source);
    }

    public Stream<Game> operatorStream() {
        // TODO: filter should be passed to getGame for serialized matching
        Stream<Game> stream = this.source.stream().map(transaction()::getGame);

        if (gameFilter != null) {
            if (gameFilter.gameHeaderFilter() != null) {
                stream = stream.filter(game -> gameFilter.gameHeaderFilter().matches(game.header()));
            }
            if (gameFilter.extendedGameHeaderFilter() != null) {
                stream = stream.filter(game -> gameFilter.extendedGameHeaderFilter().matches(game.extendedHeader()));
            }
        }
        return stream;
    }

    @Override
    public OperatorCost estimateCost() {
        OperatorCost sourceCost = source.estimateCost();

        double ratio = context().queryPlanner().gameFilterEstimate(gameFilter);
        long estimateRows = OperatorCost.capRowEstimate((int) Math.round(sourceCost.rows() * ratio));

        return ImmutableOperatorCost.builder()
                .rows(estimateRows)
                .numDeserializations(estimateRows) // Non-matching rows will mostly be caught non-deserialized
                .pageReads(context().queryPlanner().estimateGamePageReads(sourceCost.rows()))
                .build();
    }

    @Override
    public String toString() {
        if (gameFilter != null) {
            return "GameLookup(filter: " + gameFilter + ")";
        }
        return "GameLookup()";
    }

    @Override
    protected List<MetricsProvider> metricProviders() {
        return List.of(database().gameHeaderIndex(), database().extendedGameHeaderStorage());
    }
}

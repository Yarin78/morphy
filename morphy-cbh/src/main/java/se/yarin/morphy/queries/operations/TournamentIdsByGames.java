package se.yarin.morphy.queries.operations;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.Game;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.metrics.MetricsProvider;
import se.yarin.morphy.queries.QueryContext;

import java.util.List;
import java.util.stream.Stream;

public class TournamentIdsByGames extends QueryOperator<Integer> {
    private final @NotNull QueryOperator<Game> source;

    public TournamentIdsByGames(@NotNull QueryContext queryContext, @NotNull QueryOperator<Game> source) {
        super(queryContext);
        this.source = source;
    }

    @Override
    public List<QueryOperator<?>> sources() {
        return List.of(source);
    }

    @Override
    public Stream<Integer> operatorStream() {
        return source.stream().map(Game::tournamentId);
    }

    @Override
    public void estimateOperatorCost(@NotNull ImmutableOperatorCost.Builder operatorCost) {
        OperatorCost sourceCost = source.getOperatorCost();

        int entityCount = context().entityIndex(EntityType.TOURNAMENT).count();
        int gameCount = context().database().count();

        operatorCost
            .estimateRows(OperatorCost.capRowEstimate(entityCount * sourceCost.estimateRows() / gameCount))
            .estimateDeserializations(0)
            .estimatePageReads(0)
            .build();
    }

    @Override
    public String toString() {
        return "TournamentIdsByGames()";
    }

    @Override
    protected List<MetricsProvider> metricProviders() {
        return List.of();
    }
}

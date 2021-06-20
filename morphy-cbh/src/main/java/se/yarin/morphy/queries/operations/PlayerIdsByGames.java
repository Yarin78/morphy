package se.yarin.morphy.queries.operations;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.Game;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.metrics.MetricsProvider;
import se.yarin.morphy.queries.QueryContext;

import java.util.List;
import java.util.stream.Stream;

public class PlayerIdsByGames extends QueryOperator<Integer> {
    private final @NotNull QueryOperator<Game> source;

    public PlayerIdsByGames(@NotNull QueryContext queryContext, @NotNull QueryOperator<Game> source) {
        super(queryContext);
        this.source = source;
    }

    @Override
    public List<QueryOperator<?>> sources() {
        return List.of(source);
    }

    @Override
    public Stream<Integer> operatorStream() {
        return source.stream().flatMap(game -> Stream.of(game.whitePlayerId(), game.blackPlayerId()));
    }

    @Override
    public OperatorCost estimateCost() {
        OperatorCost sourceCost = source.estimateCost();

        int entityCount = context().entityIndex(EntityType.PLAYER).count();
        int gameCount = context().database().count();

        return ImmutableOperatorCost.builder()
            .rows(OperatorCost.capRowEstimate(entityCount * sourceCost.rows() / gameCount))
            .numDeserializations(0)
            .pageReads(0)
            .build();
    }

    @Override
    public String toString() {
        return "PlayerIdsByGames()";
    }

    @Override
    protected List<MetricsProvider> metricProviders() {
        return List.of();
    }
}

package se.yarin.morphy.queries.operations;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.boosters.GameEntityIndex;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.metrics.MetricsProvider;
import se.yarin.morphy.queries.QueryContext;

import java.util.List;
import java.util.stream.Stream;

public class GameIdsByEntityIds extends QueryOperator<Integer> {
    private final @NotNull QueryOperator<Integer> source;
    private final @NotNull GameEntityIndex gameEntityIndex;
    private final @NotNull EntityType entityType;

    public GameIdsByEntityIds(@NotNull QueryContext queryContext, @NotNull QueryOperator<Integer> source, @NotNull EntityType entityType) {
        super(queryContext);
        GameEntityIndex gameEntityIndex = queryContext.transaction().database().gameEntityIndex(entityType);
        if (gameEntityIndex == null) {
            throw new IllegalArgumentException("No game entity index exists for " + entityType.namePlural());
        }

        this.source = source;
        this.gameEntityIndex = gameEntityIndex;
        this.entityType = entityType;
    }

    @Override
    public List<QueryOperator<?>> sources() {
        return List.of(source);
    }

    @Override
    public Stream<Integer> operatorStream() {
        return this.source.stream().flatMap(data -> gameEntityIndex.stream(data, entityType, false));
    }

    @Override
    public void estimateOperatorCost(@NotNull ImmutableOperatorCost.Builder operatorCost) {
        OperatorCost sourceCost = source.getOperatorCost();

        int entityCount = context().entityIndex(entityType).count();
        int gameCount = context().database().count();

        long expectedMatchingGames = sourceCost.estimateRows() * gameCount / entityCount;

        operatorCost
            .estimateRows(OperatorCost.capRowEstimate(expectedMatchingGames))
            .estimateDeserializations(expectedMatchingGames / 13 + sourceCost.estimateRows())
            .estimatePageReads(context().queryPlanner().estimateGameEntityIndexPageReads(entityType, sourceCost.estimateRows()));
    }

    @Override
    public String toString() {
        return "GameIdsBy" + entityType.nameSingularCapitalized() + "Ids()";
    }

    @Override
    protected List<MetricsProvider> metricProviders() {
        return List.of(gameEntityIndex);
    }
}

package se.yarin.morphy.queries.operations;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.boosters.GameEntityIndex;
import se.yarin.morphy.entities.Entity;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.metrics.*;
import se.yarin.morphy.queries.QueryContext;

import java.util.List;
import java.util.stream.Stream;

public class GameIdsByEntities<T extends Entity & Comparable<T>> extends QueryOperator<Integer> {
    private final @NotNull QueryOperator<T> source;
    private final @NotNull GameEntityIndex gameEntityIndex;
    private final @NotNull EntityType entityType;

    public GameIdsByEntities(@NotNull QueryContext queryContext, @NotNull QueryOperator<T> source, @NotNull EntityType entityType) {
        super(queryContext);
        GameEntityIndex gameEntityIndex = transaction().database().gameEntityIndex(entityType);
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
        return this.source.stream().flatMap(data -> gameEntityIndex.stream(data.id(), entityType, false));
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
            .estimatePageReads(context().queryPlanner().estimateGameEntityIndexPageReads(entityType, sourceCost.estimateRows()))
            .build();
    }

    @Override
    public String toString() {
        return "GameIdsBy" + entityType.namePluralCapitalized() + "()";
    }

    @Override
    protected List<MetricsProvider> metricProviders() {
        return List.of(gameEntityIndex);
    }
}

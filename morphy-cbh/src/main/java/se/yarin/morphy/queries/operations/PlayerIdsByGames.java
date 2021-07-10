package se.yarin.morphy.queries.operations;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.Game;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.entities.Player;
import se.yarin.morphy.entities.Tournament;
import se.yarin.morphy.metrics.MetricsProvider;
import se.yarin.morphy.queries.QueryContext;
import se.yarin.morphy.queries.QuerySortOrder;

import java.util.List;
import java.util.stream.Stream;

public class PlayerIdsByGames extends QueryOperator<Player> {
    private final @NotNull QueryOperator<Game> source;

    public PlayerIdsByGames(@NotNull QueryContext queryContext, @NotNull QueryOperator<Game> source) {
        super(queryContext, false);
        this.source = source;
    }

    @Override
    public List<QueryOperator<?>> sources() {
        return List.of(source);
    }

    public @NotNull QuerySortOrder<Player> sortOrder() {
        return QuerySortOrder.none();
    }

    public boolean mayContainDuplicates() {
        return true;
    }

    @Override
    public Stream<QueryData<Player>> operatorStream() {
        // TODO: If not a game, map to no players
        return source.stream().flatMap(row -> Stream.of(new QueryData<>(row.data().whitePlayerId()), new QueryData<>(row.data().blackPlayerId())));
    }

    @Override
    public void estimateOperatorCost(@NotNull ImmutableOperatorCost.Builder operatorCost) {
        OperatorCost sourceCost = source.getOperatorCost();

        int entityCount = context().entityIndex(EntityType.PLAYER).count();
        int gameCount = context().database().count();

        operatorCost
            .estimateRows(OperatorCost.capRowEstimate(entityCount * sourceCost.estimateRows() / gameCount))
            .estimateDeserializations(0)
            .estimatePageReads(0)
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

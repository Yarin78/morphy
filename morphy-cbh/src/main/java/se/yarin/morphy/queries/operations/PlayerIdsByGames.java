package se.yarin.morphy.queries.operations;

import org.jetbrains.annotations.NotNull;
import se.yarin.chess.GameResult;
import se.yarin.morphy.Game;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.entities.Player;
import se.yarin.morphy.entities.Tournament;
import se.yarin.morphy.metrics.MetricsProvider;
import se.yarin.morphy.queries.GamePlayerJoinCondition;
import se.yarin.morphy.queries.QueryContext;
import se.yarin.morphy.queries.QuerySortOrder;

import java.util.List;
import java.util.stream.Stream;

public class PlayerIdsByGames extends QueryOperator<Player> {
    private final @NotNull QueryOperator<Game> source;
    private final @NotNull GamePlayerJoinCondition joinCondition;

    public PlayerIdsByGames(@NotNull QueryContext queryContext, @NotNull QueryOperator<Game> source, @NotNull GamePlayerJoinCondition joinCondition) {
        super(queryContext, false);
        this.source = source;
        this.joinCondition = joinCondition;
        assert source.hasFullData();
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
        return source.stream().flatMap(row -> {
            if (row.data().guidingText()) {
                return Stream.of();
            }
            return switch (joinCondition) {
                case WHITE -> Stream.of(new QueryData<>(row.data().whitePlayerId()));
                case BLACK -> Stream.of(new QueryData<>(row.data().blackPlayerId()));
                case ANY, BOTH ->
                        Stream.of(new QueryData<>(row.data().whitePlayerId()), new QueryData<>(row.data().blackPlayerId()));
                case WINNER -> {
                    if (row.data().result() == GameResult.WHITE_WINS) {
                        yield Stream.of(new QueryData<>(row.data().whitePlayerId()));
                    } else if (row.data().result() == GameResult.BLACK_WINS) {
                        yield Stream.of(new QueryData<>(row.data().blackPlayerId()));
                    }
                    yield Stream.of();
                }
                case LOSER -> {
                    if (row.data().result() == GameResult.WHITE_WINS) {
                        yield Stream.of(new QueryData<>(row.data().blackPlayerId()));
                    } else if (row.data().result() == GameResult.BLACK_WINS) {
                        yield Stream.of(new QueryData<>(row.data().whitePlayerId()));
                    }
                    yield Stream.of();
                }
            };
        });
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

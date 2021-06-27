package se.yarin.morphy.queries.operations;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.Game;
import se.yarin.morphy.entities.EntityIndexReadTransaction;
import se.yarin.morphy.entities.Player;
import se.yarin.morphy.entities.filters.EntityFilter;
import se.yarin.morphy.metrics.MetricsProvider;
import se.yarin.morphy.queries.GamePlayerJoinCondition;
import se.yarin.morphy.queries.QueryContext;

import java.util.List;
import java.util.stream.Stream;

public class GamePlayerFilter extends QueryOperator<Game> {
    private final @NotNull QueryOperator<Game> source;
    private final @NotNull EntityFilter<Player> playerFilter;
    private final @NotNull GamePlayerJoinCondition joinCondition;

    public GamePlayerFilter(@NotNull QueryContext queryContext,
                            @NotNull QueryOperator<Game> source,
                            @NotNull EntityFilter<Player> playerFilter,
                            @NotNull GamePlayerJoinCondition joinCondition) {
        super(queryContext, true);
        if (!source.hasFullData()) {
            throw new IllegalArgumentException("The source of GamePlayerFilter must return full data");
        }
        this.source = source;
        this.playerFilter = playerFilter;
        this.joinCondition = joinCondition;
    }

    @Override
    public List<QueryOperator<?>> sources() {
        return List.of(source);
    }

    @Override
    public Stream<QueryData<Game>> operatorStream() {
        final EntityIndexReadTransaction<Player> playerTransaction = transaction().playerTransaction();

        return this.source.stream().filter(game ->
        {
            int whiteId = game.data().whitePlayerId();
            int blackId = game.data().blackPlayerId();

            if (whiteId < 0 || blackId < 0) {
                // Happens if game is a text
                return false;
            }

            Player whitePlayer = playerTransaction.get(whiteId, playerFilter);
            Player blackPlayer = playerTransaction.get(blackId, playerFilter);

            boolean whiteMatch = whitePlayer != null && playerFilter.matches(whitePlayer);
            boolean blackMatch = blackPlayer != null && playerFilter.matches(blackPlayer);

            switch (joinCondition) {
                case ANY -> {
                    return whiteMatch || blackMatch;
                }
                case BOTH -> {
                    return whiteMatch && blackMatch;
                }
                case WHITE -> {
                    return whiteMatch;
                }
                case BLACK -> {
                    return blackMatch;
                }
                case WINNER -> {
                    switch (game.data().result()) {
                        case WHITE_WINS, WHITE_WINS_ON_FORFEIT -> {
                            return whiteMatch;
                        }
                        case BLACK_WINS, BLACK_WINS_ON_FORFEIT -> {
                            return blackMatch;
                        }
                        case DRAW, BOTH_LOST, NOT_FINISHED, DRAW_ON_FORFEIT -> {
                            return false;
                        }
                    }
                }
                case LOSER -> {
                    switch (game.data().result()) {
                        case WHITE_WINS, WHITE_WINS_ON_FORFEIT -> {
                            return blackMatch;
                        }
                        case BLACK_WINS, BLACK_WINS_ON_FORFEIT -> {
                            return whiteMatch;
                        }
                        case DRAW, NOT_FINISHED, DRAW_ON_FORFEIT -> {
                            return false;
                        }
                        case BOTH_LOST -> { return whiteMatch || blackMatch; }
                    }
                }
            }
            return false;
        });
    }

    @Override
    public void estimateOperatorCost(@NotNull ImmutableOperatorCost.Builder operatorCost) {
        OperatorCost sourceCost = source.getOperatorCost();

        double ratio = context().queryPlanner().playerFilterEstimate(playerFilter);
        double anyRatio = 1 - (1-ratio) * (1-ratio); // ratio than any of the two players matches the filter

        operatorCost
            .estimateRows(OperatorCost.capRowEstimate(sourceCost.estimateRows() * anyRatio))
            //.estimatePageReads(context().queryPlanner().estimatePlayerPageReads(sourceCost.estimateRows()))
            .estimatePageReads(sourceCost.estimateRows()) // The reads will be scattered; TODO: But if very many read, the disk cache will work nice anyhow
            .estimateDeserializations(2 * OperatorCost.capRowEstimate(sourceCost.estimateRows() * anyRatio))
            .build();
    }

    @Override
    public String toString() {
        return "GamePlayerFilter(filter: " + playerFilter + ")";
    }

    @Override
    protected List<MetricsProvider> metricProviders() {
        return List.of(database().playerIndex());
    }
}
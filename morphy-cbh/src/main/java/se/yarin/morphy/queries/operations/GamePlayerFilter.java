package se.yarin.morphy.queries.operations;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.Game;
import se.yarin.morphy.entities.EntityIndexReadTransaction;
import se.yarin.morphy.entities.Player;
import se.yarin.morphy.entities.filters.EntityFilter;
import se.yarin.morphy.metrics.MetricsProvider;
import se.yarin.morphy.queries.QueryContext;

import java.util.List;
import java.util.stream.Stream;

public class GamePlayerFilter extends QueryOperator<Game> {
    private final @NotNull QueryOperator<Game> source;
    private final @NotNull EntityFilter<Player> playerFilter;

    public GamePlayerFilter(@NotNull QueryContext queryContext, @NotNull QueryOperator<Game> source, @NotNull EntityFilter<Player> playerFilter) {
        super(queryContext, true);
        if (!source.hasFullData()) {
            throw new IllegalArgumentException("The source of GamePlayerFilter must return full data");
        }
        this.source = source;
        this.playerFilter = playerFilter;
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
            Player whitePlayer = game.data().whitePlayerId() >= 0 ? playerTransaction.get(game.data().whitePlayerId(), playerFilter) : null;
            Player blackPlayer = game.data().blackPlayerId() >= 0 ? playerTransaction.get(game.data().blackPlayerId(), playerFilter) : null;

            return (whitePlayer != null && playerFilter.matches(whitePlayer)) ||
                    (blackPlayer != null && playerFilter.matches(blackPlayer));
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
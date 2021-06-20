package se.yarin.morphy.queries.operations;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.Game;
import se.yarin.morphy.entities.EntityIndexReadTransaction;
import se.yarin.morphy.entities.Player;
import se.yarin.morphy.entities.filters.EntityFilter;
import se.yarin.morphy.metrics.FileMetrics;
import se.yarin.morphy.metrics.ItemMetrics;
import se.yarin.morphy.metrics.MetricsProvider;
import se.yarin.morphy.metrics.MetricsRepository;
import se.yarin.morphy.queries.QueryContext;

import java.util.List;
import java.util.stream.Stream;

public class GamePlayerFilter extends QueryOperator<Game> {
    private final @NotNull QueryOperator<Game> source;
    private final @NotNull EntityFilter<Player> playerFilter;

    public GamePlayerFilter(@NotNull QueryContext queryContext, @NotNull QueryOperator<Game> source, @NotNull EntityFilter<Player> playerFilter) {
        super(queryContext);
        this.source = source;
        this.playerFilter = playerFilter;
    }

    @Override
    public List<QueryOperator<?>> sources() {
        return List.of(source);
    }

    @Override
    public Stream<Game> operatorStream() {
        final EntityIndexReadTransaction<Player> playerTransaction = transaction().playerTransaction();

        return this.source.stream().filter(game ->
        {
            Player whitePlayer = playerTransaction.get(game.whitePlayerId(), playerFilter);
            Player blackPlayer = playerTransaction.get(game.blackPlayerId(), playerFilter);

            return (whitePlayer != null && playerFilter.matches(whitePlayer)) ||
                    (blackPlayer != null && playerFilter.matches(blackPlayer));
        });
    }

    @Override
    public OperatorCost estimateCost() {
        OperatorCost sourceCost = source.estimateCost();

        double ratio = context().queryPlanner().playerFilterEstimate(playerFilter);
        double anyRatio = 1 - (1-ratio) * (1-ratio); // ratio than any of the two players matches the filter

        return ImmutableOperatorCost.builder()
                .rows(OperatorCost.capRowEstimate(sourceCost.rows() * anyRatio))
                //.pageReads(context().queryPlanner().estimatePlayerPageReads(sourceCost.rows()))
                .pageReads(sourceCost.rows()) // The reads will be scattered; TODO: But if very many read, the disk cache will work nice anyhow
                .numDeserializations(2 * OperatorCost.capRowEstimate(sourceCost.rows() * anyRatio))
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
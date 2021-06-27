package se.yarin.morphy.queries.operations;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.Game;
import se.yarin.morphy.games.filters.GameFilter;
import se.yarin.morphy.metrics.MetricsProvider;
import se.yarin.morphy.queries.QueryContext;

import java.util.List;
import java.util.stream.Stream;

public class GameTableScan extends QueryOperator<Game> {
    private final @Nullable GameFilter gameFilter;
    private final int firstGameId;

    public GameTableScan(@NotNull QueryContext queryContext, @Nullable GameFilter gameFilter) {
        this(queryContext, gameFilter, 1);
    }

    public GameTableScan(@NotNull QueryContext queryContext, @Nullable GameFilter gameFilter, int firstGameId) {
        super(queryContext, true);
        this.gameFilter = gameFilter;
        this.firstGameId = firstGameId;
    }

    @Override
    public List<QueryOperator<?>> sources() {
        return List.of();
    }

    @Override
    public Stream<QueryData<Game>> operatorStream() {
        return transaction().stream(firstGameId, gameFilter).map(QueryData::new);
    }

    @Override
    public void estimateOperatorCost(@NotNull ImmutableOperatorCost.Builder operatorCost) {
        int totalGames = context().database().count();
        int numScannedGames = Math.max(0, totalGames - (firstGameId - 1));
        double scanRatio = 1.0 * numScannedGames / totalGames;
        double matchingRatio = context().queryPlanner().gameFilterEstimate(gameFilter);
        long estimateRows = OperatorCost.capRowEstimate((int) Math.round(numScannedGames * matchingRatio));

        long pageReads = Math.round((context().database().gameHeaderIndex().numDiskPages() +
                context().database().extendedGameHeaderStorage().numDiskPages()) * scanRatio);

        operatorCost
            .estimateRows(estimateRows)
            .estimateDeserializations(estimateRows * 2) // Non-matching rows will mostly be caught non-deserialized
            .estimatePageReads(pageReads);
    }

    @Override
    public String toString() {
        if (gameFilter != null) {
            return "GameTableScan(filter: " + gameFilter + ")";
        }
        return "GameTableScan()";
    }

    @Override
    protected List<MetricsProvider> metricProviders() {
        return List.of(database().gameHeaderIndex(), database().extendedGameHeaderStorage());
    }
}

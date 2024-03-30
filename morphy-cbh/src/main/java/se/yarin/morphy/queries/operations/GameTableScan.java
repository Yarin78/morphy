package se.yarin.morphy.queries.operations;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.Game;
import se.yarin.morphy.games.filters.GameFilter;
import se.yarin.morphy.metrics.MetricsProvider;
import se.yarin.morphy.queries.QueryContext;
import se.yarin.morphy.queries.QuerySortOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class GameTableScan extends QueryOperator<Game> {
    private final @Nullable GameFilter gameFilter;
    private final @Nullable Integer startId;
    private final @Nullable Integer endId;

    public GameTableScan(@NotNull QueryContext queryContext, @Nullable GameFilter gameFilter) {
        this(queryContext, gameFilter, null, null);
    }

    public GameTableScan(@NotNull QueryContext queryContext, @Nullable GameFilter gameFilter, @Nullable Integer startId, @Nullable Integer endId) {
        super(queryContext, true);
        this.gameFilter = gameFilter;
        this.startId = startId;
        this.endId = endId;
    }

    @Override
    public List<QueryOperator<?>> sources() {
        return List.of();
    }

    public @NotNull QuerySortOrder<Game> sortOrder() {
        return QuerySortOrder.byId();
    }

    public boolean mayContainDuplicates() {
        return false;
    }

    @Override
    public Stream<QueryData<Game>> operatorStream() {
        return transaction().stream(startId, endId, gameFilter).map(QueryData::new);
    }

    @Override
    public void estimateOperatorCost(@NotNull ImmutableOperatorCost.Builder operatorCost) {
        int totalGames = context().database().count();
        int startId = this.startId == null ? 1 : this.startId;
        int endId = this.endId == null ? totalGames + 1 : this.endId;
        int numScannedGames = Math.max(0, endId - startId);
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
        ArrayList<String> params = new ArrayList<>();
        if (startId != null) {
            params.add("startId: " + startId);
        }
        if (endId != null) {
            params.add("endId: " + endId);
        }
        if (gameFilter != null) {
            params.add("filter: " + gameFilter);
        }
        return "GameTableScan(" + String.join(", ", params) + ")";
    }

    @Override
    protected List<MetricsProvider> metricProviders() {
        return List.of(database().gameHeaderIndex(), database().extendedGameHeaderStorage());
    }
}

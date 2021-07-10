package se.yarin.morphy.queries.operations;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.entities.EntityIndexReadTransaction;
import se.yarin.morphy.entities.Player;
import se.yarin.morphy.entities.Tournament;
import se.yarin.morphy.entities.filters.EntityFilter;
import se.yarin.morphy.metrics.MetricsProvider;
import se.yarin.morphy.queries.QueryContext;
import se.yarin.morphy.queries.QuerySortOrder;

import java.util.List;
import java.util.stream.Stream;

public class PlayerIndexRangeScan extends QueryOperator<Player> {
    private final EntityIndexReadTransaction<Player> txn;
    private final @Nullable EntityFilter<Player> playerFilter;
    private final @NotNull Player rangeStart, rangeEnd;

    public PlayerIndexRangeScan(@NotNull QueryContext queryContext, @Nullable EntityFilter<Player> playerFilter, @NotNull Player rangeStart, @NotNull Player rangeEnd) {
        super(queryContext, true);
        this.txn = queryContext.transaction().playerTransaction();
        this.playerFilter = playerFilter;
        this.rangeStart = rangeStart;
        this.rangeEnd = rangeEnd;
    }

    @Override
    public List<QueryOperator<?>> sources() {
        return List.of();
    }

    public @NotNull QuerySortOrder<Player> sortOrder() {
        return QuerySortOrder.byPlayerDefaultIndex();
    }

    public boolean mayContainDuplicates() {
        return false;
    }

    public Stream<QueryData<Player>> operatorStream() {
        return txn.streamOrderedAscending(rangeStart, rangeEnd, playerFilter).map(QueryData::new);
    }

    @Override
    public void estimateOperatorCost(@NotNull ImmutableOperatorCost.Builder operatorCost) {
        long scanCount = context().queryPlanner().playerRangeEstimate(rangeStart, rangeEnd, null);
        long matchCount = context().queryPlanner().playerRangeEstimate(rangeStart, rangeEnd, playerFilter);

        operatorCost
            .estimateRows(matchCount)
            .estimatePageReads(context().queryPlanner().estimatePlayerPageReads(scanCount))
            .estimateDeserializations(matchCount);
    }

    @Override
    public String toString() {
        return "PlayerIndexRangeScan(start: '" + rangeStart + "', end: '" + rangeEnd + "', filter: " + playerFilter + ")";
    }

    @Override
    protected List<MetricsProvider> metricProviders() {
        return List.of(database().playerIndex());
    }
}

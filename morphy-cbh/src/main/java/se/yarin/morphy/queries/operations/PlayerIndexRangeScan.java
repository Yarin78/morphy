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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class PlayerIndexRangeScan extends QueryOperator<Player> {
    private final EntityIndexReadTransaction<Player> txn;
    private final @Nullable EntityFilter<Player> playerFilter;
    private final @Nullable Player rangeStart, rangeEnd;
    private final boolean reverse;

    public PlayerIndexRangeScan(@NotNull QueryContext queryContext, @Nullable EntityFilter<Player> playerFilter, @Nullable Player rangeStart, @Nullable Player rangeEnd, boolean reverse) {
        super(queryContext, true);
        this.txn = queryContext.transaction().playerTransaction();
        this.playerFilter = playerFilter;
        this.rangeStart = rangeStart;
        this.rangeEnd = rangeEnd;
        this.reverse = reverse; // If true, rangeStart should be greater than rangeEnd
    }

    @Override
    public List<QueryOperator<?>> sources() {
        return List.of();
    }

    public @NotNull QuerySortOrder<Player> sortOrder() {
        return QuerySortOrder.byPlayerDefaultIndex(this.reverse);
    }

    public boolean mayContainDuplicates() {
        return false;
    }

    public Stream<QueryData<Player>> operatorStream() {
        if (!this.reverse) {
            return txn.streamOrderedAscending(rangeStart, rangeEnd, playerFilter).map(QueryData::new);
        } else {
            return txn.streamOrderedDescending(rangeStart, rangeEnd, playerFilter).map(QueryData::new);
        }
    }

    @Override
    public void estimateOperatorCost(@NotNull ImmutableOperatorCost.Builder operatorCost) {
        long scanCount = context().queryPlanner().playerRangeEstimate(!reverse ? rangeStart : rangeEnd, !reverse ? rangeEnd : rangeStart, null);
        long matchCount = context().queryPlanner().playerRangeEstimate(!reverse ? rangeStart : rangeEnd, !reverse ? rangeEnd : rangeStart, playerFilter);

        operatorCost
            .estimateRows(matchCount)
            .estimatePageReads(context().queryPlanner().estimatePlayerPageReads(scanCount))
            .estimateDeserializations(matchCount);
    }

    @Override
    public String toString() {
        ArrayList<String> params = new ArrayList<>();
        if (rangeStart != null) {
            params.add("start: '" + rangeStart.getFullName() + "'");
        }
        if (rangeEnd != null) {
            params.add("end: '" + rangeEnd.getFullName() + "'");
        }
        if (playerFilter != null) {
            params.add("filter: " + playerFilter);
        }
        if (reverse) {
            params.add("reverse: true");
        }
        return "PlayerIndexRangeScan(" + String.join(", ", params) + ")";
    }

    @Override
    protected List<MetricsProvider> metricProviders() {
        return List.of(database().playerIndex());
    }
}

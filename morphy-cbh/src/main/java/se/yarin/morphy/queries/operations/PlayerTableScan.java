package se.yarin.morphy.queries.operations;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.entities.EntityIndexReadTransaction;
import se.yarin.morphy.entities.Player;
import se.yarin.morphy.entities.filters.EntityFilter;
import se.yarin.morphy.metrics.MetricsProvider;
import se.yarin.morphy.queries.QueryContext;
import se.yarin.morphy.queries.QuerySortOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Scans the Player entity base in ID order from a given player ID.
 * Optionally filters out players on a filter predicate.
 */
public class PlayerTableScan extends QueryOperator<Player> {
    private final @NotNull EntityIndexReadTransaction<Player> txn;
    private final @Nullable EntityFilter<Player> playerFilter;
    private final @Nullable Integer startId;
    private final @Nullable Integer endId;

    public PlayerTableScan(@NotNull QueryContext queryContext, @Nullable EntityFilter<Player> playerFilter) {
        this(queryContext, playerFilter, null, null);
    }

    public PlayerTableScan(@NotNull QueryContext queryContext, @Nullable EntityFilter<Player> playerFilter, @Nullable Integer startId, @Nullable Integer endId) {
        super(queryContext, true);
        this.txn = transaction().playerTransaction();
        this.playerFilter = playerFilter;
        this.startId = startId;
        this.endId = endId;
    }

    @Override
    public List<QueryOperator<?>> sources() {
        return List.of();
    }

    public @NotNull QuerySortOrder<Player> sortOrder() {
        return QuerySortOrder.byId();
    }

    public boolean mayContainDuplicates() {
        return false;
    }

    @Override
    public Stream<QueryData<Player>> operatorStream() {
        return txn.stream(this.startId, this.endId, this.playerFilter).map(QueryData::new);
    }

    @Override
    public void estimateOperatorCost(@NotNull ImmutableOperatorCost.Builder operatorCost) {
        int totalPlayers = context().database().playerIndex().count();
        int startId = this.startId == null ? 0 : this.startId;
        int endId = this.endId == null ? totalPlayers : this.endId;
        int numScannedPlayers = Math.max(0, endId - startId);
        double scanRatio = 1.0 * numScannedPlayers / totalPlayers;
        double matchingRatio = context().queryPlanner().playerFilterEstimate(playerFilter);
        long estimateRows = OperatorCost.capRowEstimate((int) Math.round(numScannedPlayers * matchingRatio));

        long pageReads = Math.round(context().database().playerIndex().numDiskPages() * scanRatio);

        operatorCost
            .estimateRows(estimateRows)
            .estimateDeserializations(estimateRows) // Non-matching rows will mostly be caught non-deserialized
            .estimatePageReads(pageReads)
            .build();
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
        if (playerFilter != null) {
            params.add("filter: " + playerFilter);
        }

        return "PlayerTableScan(" + String.join(", ", params) + ")";
    }

    @Override
    protected List<MetricsProvider> metricProviders() {
        return List.of(database().playerIndex());
    }
}

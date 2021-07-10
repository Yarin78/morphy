package se.yarin.morphy.queries.operations;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.entities.EntityIndexReadTransaction;
import se.yarin.morphy.entities.Player;
import se.yarin.morphy.entities.filters.EntityFilter;
import se.yarin.morphy.metrics.MetricsProvider;
import se.yarin.morphy.queries.QueryContext;
import se.yarin.morphy.queries.QuerySortOrder;

import java.util.List;
import java.util.stream.Stream;

public class PlayerTableScan extends QueryOperator<Player> {
    private final @NotNull EntityIndexReadTransaction<Player> txn;
    private final @Nullable EntityFilter<Player> playerFilter;
    private final int firstPlayerId;

    public PlayerTableScan(@NotNull QueryContext queryContext, @Nullable EntityFilter<Player> playerFilter) {
        this(queryContext, playerFilter, 0);
    }

    public PlayerTableScan(@NotNull QueryContext queryContext, @Nullable EntityFilter<Player> playerFilter, int firstId) {
        super(queryContext, true);
        this.txn = transaction().playerTransaction();
        this.playerFilter = playerFilter;
        this.firstPlayerId = firstId;
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
        return txn.stream(firstPlayerId, this.playerFilter).map(QueryData::new);
    }

    @Override
    public void estimateOperatorCost(@NotNull ImmutableOperatorCost.Builder operatorCost) {
        int totalPlayers = context().database().playerIndex().count();
        int numScannedPlayers = Math.max(0, totalPlayers - firstPlayerId);
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
        if (playerFilter != null) {
            return "PlayerTableScan(filter: " + playerFilter + ")";
        }
        return "PlayerTableScan()";
    }

    @Override
    protected List<MetricsProvider> metricProviders() {
        return List.of(database().playerIndex());
    }
}

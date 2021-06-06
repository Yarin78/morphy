package se.yarin.morphy.queries.operations;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.entities.EntityIndexReadTransaction;
import se.yarin.morphy.entities.Player;
import se.yarin.morphy.entities.filters.EntityFilter;
import se.yarin.morphy.queries.QueryContext;

import java.util.List;
import java.util.stream.Stream;

public class PlayerTableScan extends QueryOperator<Player> {
    private final @NotNull EntityIndexReadTransaction<Player> txn;
    private final @Nullable EntityFilter<Player> playerFilter;

    public PlayerTableScan(@NotNull QueryContext queryContext, @Nullable EntityFilter<Player> playerFilter) {
        super(queryContext);
        this.txn = transaction().playerTransaction();
        this.playerFilter = playerFilter;
    }

    @Override
    public List<QueryOperator<?>> sources() {
        return List.of();
    }

    @Override
    public Stream<Player> operatorStream() {
        return txn.stream(this.playerFilter);
    }

    @Override
    public OperatorCost estimateCost() {
        int numPlayers = context().database().playerIndex().count();
        double ratio = context().queryPlanner().playerFilterEstimate(playerFilter);
        long estimateRows = OperatorCost.capRowEstimate((int) Math.round(numPlayers * ratio));

        long pageReads = context().database().playerIndex().numDiskPages();

        return ImmutableOperatorCost.builder()
                .rows(estimateRows)
                .numDeserializations(estimateRows) // Non-matching rows will mostly be caught non-deserialized
                .pageReads(pageReads)
                .build();
    }

    @Override
    public String toString() {
        if (playerFilter != null) {
            return "PlayerTableScan(filter: " + playerFilter + ")";
        }
        return "PlayerTableScan()";
    }
}

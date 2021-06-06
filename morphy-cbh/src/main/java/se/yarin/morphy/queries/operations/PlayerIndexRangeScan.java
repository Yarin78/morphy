package se.yarin.morphy.queries.operations;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.entities.EntityIndexReadTransaction;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.entities.Player;
import se.yarin.morphy.entities.filters.EntityFilter;
import se.yarin.morphy.queries.QueryContext;
import se.yarin.util.PagedBlobChannel;

import java.util.List;
import java.util.stream.Stream;

public class PlayerIndexRangeScan extends QueryOperator<Player> {
    private final EntityIndexReadTransaction<Player> txn;
    private final @Nullable EntityFilter<Player> playerFilter;
    private final @NotNull Player rangeStart, rangeEnd;

    public PlayerIndexRangeScan(@NotNull QueryContext queryContext, @Nullable EntityFilter<Player> playerFilter, @NotNull Player rangeStart, @NotNull Player rangeEnd) {
        super(queryContext);
        this.txn = queryContext.transaction().playerTransaction();
        this.playerFilter = playerFilter;
        this.rangeStart = rangeStart;
        this.rangeEnd = rangeEnd;
    }

    @Override
    public List<QueryOperator<?>> sources() {
        return List.of();
    }

    public Stream<Player> operatorStream() {
        return txn.streamOrderedAscending(rangeStart, rangeEnd, playerFilter);
    }

    @Override
    public OperatorCost estimateCost() {
        long scanCount = context().queryPlanner().playerRangeEstimate(rangeStart, rangeEnd, null);
        long matchCount = context().queryPlanner().playerRangeEstimate(rangeStart, rangeEnd, playerFilter);

        int entitySize = context().entityIndex(EntityType.PLAYER).storageHeader().entitySize();

        return ImmutableOperatorCost.builder()
                .rows(matchCount)
                .pageReads(1 + entitySize * scanCount / PagedBlobChannel.PAGE_SIZE)
                .numDeserializations(matchCount)
                .build();
    }

    @Override
    public String toString() {
        return "PlayerIndexRangeScan(start: '" + rangeStart + "', end: '" + rangeEnd + "', filter: " + playerFilter + ")";
    }
}

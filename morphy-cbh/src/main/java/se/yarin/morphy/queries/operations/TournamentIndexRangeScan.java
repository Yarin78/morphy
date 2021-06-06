package se.yarin.morphy.queries.operations;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.entities.EntityIndexReadTransaction;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.entities.Tournament;
import se.yarin.morphy.entities.filters.EntityFilter;
import se.yarin.morphy.queries.QueryContext;
import se.yarin.util.PagedBlobChannel;

import java.util.List;
import java.util.stream.Stream;

public class TournamentIndexRangeScan extends QueryOperator<Tournament> {
    private final EntityIndexReadTransaction<Tournament> txn;
    private final @Nullable EntityFilter<Tournament> tournamentFilter;
    private final @NotNull Tournament rangeStart, rangeEnd;

    public TournamentIndexRangeScan(@NotNull QueryContext queryContext, @Nullable EntityFilter<Tournament> tournamentFilter, @NotNull Tournament rangeStart, @NotNull Tournament rangeEnd) {
        super(queryContext);
        this.txn = queryContext.transaction().tournamentTransaction();
        this.tournamentFilter = tournamentFilter;
        this.rangeStart = rangeStart;
        this.rangeEnd = rangeEnd;
    }

    @Override
    public List<QueryOperator<?>> sources() {
        return List.of();
    }

    public Stream<Tournament> operatorStream() {
        return txn.streamOrderedAscending(rangeStart, rangeEnd, tournamentFilter);
    }

    @Override
    public OperatorCost estimateCost() {
        long scanCount = context().queryPlanner().tournamentRangeEstimate(rangeStart, rangeEnd, null);
        long matchCount = context().queryPlanner().tournamentRangeEstimate(rangeStart, rangeEnd, tournamentFilter);

        int entitySize = context().entityIndex(EntityType.TOURNAMENT).storageHeader().entitySize();
        int extraSize = context().database().tournamentExtraStorage().storageHeader().recordSize();

        return ImmutableOperatorCost.builder()
                .rows(matchCount)
                .pageReads(2 + entitySize * scanCount / PagedBlobChannel.PAGE_SIZE + extraSize * scanCount / PagedBlobChannel.PAGE_SIZE)
                .numDeserializations(matchCount)
                .build();
    }

    @Override
    public String toString() {
        return "TournamentIndexRangeScan(start: '" + rangeStart + "', end: '" + rangeEnd + "', filter: " + tournamentFilter + ")";
    }
}

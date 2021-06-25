package se.yarin.morphy.queries.operations;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.entities.EntityIndexReadTransaction;
import se.yarin.morphy.entities.Tournament;
import se.yarin.morphy.entities.filters.EntityFilter;
import se.yarin.morphy.metrics.MetricsProvider;
import se.yarin.morphy.queries.QueryContext;

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
    public void estimateOperatorCost(@NotNull ImmutableOperatorCost.Builder operatorCost) {
        long scanCount = context().queryPlanner().tournamentRangeEstimate(rangeStart, rangeEnd, null);
        long matchCount = context().queryPlanner().tournamentRangeEstimate(rangeStart, rangeEnd, tournamentFilter);

        operatorCost
            .estimateRows(matchCount)
            .estimatePageReads(context().queryPlanner().estimateTournamentPageReads(scanCount))
            .estimateDeserializations(matchCount)
            .build();
    }

    @Override
    public String toString() {
        return "TournamentIndexRangeScan(start: '" + rangeStart + "', end: '" + rangeEnd + "', filter: " + tournamentFilter + ")";
    }

    @Override
    protected List<MetricsProvider> metricProviders() {
        return List.of(database().tournamentIndex(), database().tournamentExtraStorage());
    }
}

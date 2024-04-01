package se.yarin.morphy.queries.operations;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.entities.EntityIndexReadTransaction;
import se.yarin.morphy.entities.Tournament;
import se.yarin.morphy.entities.filters.EntityFilter;
import se.yarin.morphy.metrics.MetricsProvider;
import se.yarin.morphy.queries.QueryContext;
import se.yarin.morphy.queries.QuerySortOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class TournamentIndexRangeScan extends QueryOperator<Tournament> {
    private final EntityIndexReadTransaction<Tournament> txn;
    private final @Nullable EntityFilter<Tournament> tournamentFilter;
    private final @Nullable Tournament rangeStart, rangeEnd;
    private final boolean reverse;

    public TournamentIndexRangeScan(@NotNull QueryContext queryContext, @Nullable EntityFilter<Tournament> tournamentFilter, @Nullable Tournament rangeStart, @Nullable Tournament rangeEnd, boolean reverse) {
        super(queryContext, true);
        this.txn = queryContext.transaction().tournamentTransaction();
        this.tournamentFilter = tournamentFilter;
        this.rangeStart = rangeStart;
        this.rangeEnd = rangeEnd;
        this.reverse = reverse; // If true, rangeStart should be greater than rangeEnd
    }

    @Override
    public List<QueryOperator<?>> sources() {
        return List.of();
    }

    public @NotNull QuerySortOrder<Tournament> sortOrder() {
        return QuerySortOrder.byTournamentDefaultIndex();
    }

    public boolean mayContainDuplicates() {
        return false;
    }

    public Stream<QueryData<Tournament>> operatorStream() {
        if (!reverse) {
            return txn.streamOrderedAscending(rangeStart, rangeEnd, tournamentFilter).map(QueryData::new);
        } else {
            return txn.streamOrderedDescending(rangeStart, rangeEnd, tournamentFilter).map(QueryData::new);
        }
    }

    @Override
    public void estimateOperatorCost(@NotNull ImmutableOperatorCost.Builder operatorCost) {
        long scanCount = context().queryPlanner().tournamentRangeEstimate(!reverse ? rangeStart : rangeEnd, !reverse ? rangeEnd : rangeStart, null);
        long matchCount = context().queryPlanner().tournamentRangeEstimate(!reverse ? rangeStart : rangeEnd, !reverse ? rangeEnd : rangeStart, tournamentFilter);

        operatorCost
            .estimateRows(matchCount)
            .estimatePageReads(context().queryPlanner().estimateTournamentPageReads(scanCount))
            .estimateDeserializations(matchCount)
            .build();
    }

    @Override
    public String toString() {
        ArrayList<String> params = new ArrayList<>();
        if (rangeStart != null) {
            params.add("start: '" + rangeStart + "'");
        }
        if (rangeEnd != null) {
            params.add("end: '" + rangeEnd + "'");
        }
        if (tournamentFilter != null) {
            params.add("filter: " + tournamentFilter);
        }
        if (reverse) {
            params.add("reverse: true");
        }
        return "TournamentIndexRangeScan(" + String.join(", ", params) + ")";
    }

    @Override
    protected List<MetricsProvider> metricProviders() {
        return List.of(database().tournamentIndex(), database().tournamentExtraStorage());
    }
}

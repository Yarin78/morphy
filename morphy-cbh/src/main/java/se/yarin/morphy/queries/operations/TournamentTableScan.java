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

public class TournamentTableScan extends QueryOperator<Tournament> {
    private final @NotNull EntityIndexReadTransaction<Tournament> txn;
    private final @Nullable EntityFilter<Tournament> tournamentFilter;

    private final @Nullable Integer startId;
    private final @Nullable Integer endId;


    public TournamentTableScan(@NotNull QueryContext queryContext, @Nullable EntityFilter<Tournament> tournamentFilter) {
        this(queryContext, tournamentFilter, null, null);
    }

    public TournamentTableScan(@NotNull QueryContext queryContext, @Nullable EntityFilter<Tournament> tournamentFilter, @Nullable Integer startId, @Nullable Integer endId) {
        super(queryContext, true);
        this.txn = transaction().tournamentTransaction();
        this.tournamentFilter = tournamentFilter;
        this.startId = startId;
        this.endId = endId;
    }

    @Override
    public List<QueryOperator<?>> sources() {
        return List.of();
    }

    public @NotNull QuerySortOrder<Tournament> sortOrder() {
        return QuerySortOrder.byId();
    }

    public boolean mayContainDuplicates() {
        return false;
    }

    @Override
    public Stream<QueryData<Tournament>> operatorStream() {
        return txn.stream(this.tournamentFilter).map(QueryData::new);
    }

    @Override
    public void estimateOperatorCost(@NotNull ImmutableOperatorCost.Builder operatorCost) {
        int numTournaments = context().database().tournamentIndex().count();
        double ratio = tournamentFilter == null ? 1.0 : tournamentFilter.expectedMatch(context().queryPlanner());
        long estimateRows = OperatorCost.capRowEstimate((int) Math.round(numTournaments * ratio));

        long pageReads = context().database().tournamentIndex().numDiskPages();

        operatorCost
            .estimateRows(estimateRows)
            .estimateDeserializations(estimateRows) // Non-matching rows will mostly be caught non-deserialized
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
        if (tournamentFilter != null) {
            params.add("filter: " + tournamentFilter);
        }

        return "TournamentTableScan(" + String.join(", ", params) + ")";
    }

    @Override
    protected List<MetricsProvider> metricProviders() {
        return List.of(database().tournamentIndex(), database().tournamentExtraStorage());
    }
}
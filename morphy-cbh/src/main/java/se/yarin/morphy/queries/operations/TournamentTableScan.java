package se.yarin.morphy.queries.operations;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.entities.EntityIndexReadTransaction;
import se.yarin.morphy.entities.Tournament;
import se.yarin.morphy.entities.filters.EntityFilter;
import se.yarin.morphy.metrics.MetricsProvider;
import se.yarin.morphy.queries.QueryContext;
import se.yarin.morphy.queries.QuerySortOrder;

import java.util.List;
import java.util.stream.Stream;

public class TournamentTableScan extends QueryOperator<Tournament> {
    private final @NotNull EntityIndexReadTransaction<Tournament> txn;
    private final @Nullable EntityFilter<Tournament> tournamentFilter;

    private final int firstTournamentId;

    public TournamentTableScan(@NotNull QueryContext queryContext, @Nullable EntityFilter<Tournament> tournamentFilter) {
        this(queryContext, tournamentFilter, 0);
    }

    public TournamentTableScan(@NotNull QueryContext queryContext, @Nullable EntityFilter<Tournament> tournamentFilter, int firstTournamentId) {
        super(queryContext, true);
        this.txn = transaction().tournamentTransaction();
        this.tournamentFilter = tournamentFilter;
        this.firstTournamentId = firstTournamentId;
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
        if (tournamentFilter != null) {
            return "TournamentTableScan(firstId: " + firstTournamentId + ", filter: " + tournamentFilter + ")";
        }
        return "TournamentTableScan(firstId: " + firstTournamentId + ")";
    }

    @Override
    protected List<MetricsProvider> metricProviders() {
        return List.of(database().tournamentIndex(), database().tournamentExtraStorage());
    }
}
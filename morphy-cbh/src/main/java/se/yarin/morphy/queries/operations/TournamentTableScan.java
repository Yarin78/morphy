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

public class TournamentTableScan extends QueryOperator<Tournament> {
    private final @NotNull EntityIndexReadTransaction<Tournament> txn;
    private final @Nullable EntityFilter<Tournament> tournamentFilter;

    public TournamentTableScan(@NotNull QueryContext queryContext, @Nullable EntityFilter<Tournament> tournamentFilter) {
        super(queryContext);
        this.txn = transaction().tournamentTransaction();
        this.tournamentFilter = tournamentFilter;
    }

    @Override
    public List<QueryOperator<?>> sources() {
        return List.of();
    }

    @Override
    public Stream<Tournament> operatorStream() {
        return txn.stream(this.tournamentFilter);
    }

    @Override
    public OperatorCost estimateCost() {
        int numTournaments = context().database().tournamentIndex().count();
        double ratio = tournamentFilter == null ? 1.0 : tournamentFilter.expectedMatch(context().queryPlanner());
        long estimateRows = OperatorCost.capRowEstimate((int) Math.round(numTournaments * ratio));

        long pageReads = context().database().tournamentIndex().numDiskPages();

        return ImmutableOperatorCost.builder()
                .rows(estimateRows)
                .numDeserializations(estimateRows) // Non-matching rows will mostly be caught non-deserialized
                .pageReads(pageReads)
                .build();
    }

    @Override
    public String toString() {
        if (tournamentFilter != null) {
            return "TournamentTableScan(filter: " + tournamentFilter + ")";
        }
        return "TournamentTableScan()";
    }

    @Override
    protected List<MetricsProvider> metricProviders() {
        return List.of(database().tournamentIndex(), database().tournamentExtraStorage());
    }
}
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

public class TournamentLookup extends QueryOperator<Tournament> {
    private final @Nullable EntityFilter<Tournament> tournamentFilter;
    private final @NotNull QueryOperator<Tournament> source;
    private final @NotNull EntityIndexReadTransaction<Tournament> txn;

    public TournamentLookup(@NotNull QueryContext queryContext, @NotNull QueryOperator<Tournament> source, @Nullable EntityFilter<Tournament> tournamentFilter) {
        super(queryContext, true);
        this.txn = transaction().tournamentTransaction();
        this.source = source;
        this.tournamentFilter = tournamentFilter;
    }

    @Override
    public List<QueryOperator<?>> sources() {
        return List.of(source);
    }

    public Stream<QueryData<Tournament>> operatorStream() {
        return this.source.stream()
                .flatMap(data -> {
                    Tournament tournament = txn.get(data.id(), tournamentFilter);
                    if (tournament == null) {
                        return Stream.of();
                    } else {
                        return Stream.of(new QueryData<>(data.id(), tournament, data.weight()));
                    }
                });
    }

    @Override
    public void estimateOperatorCost(@NotNull ImmutableOperatorCost.Builder operatorCost) {
        OperatorCost sourceCost = source.getOperatorCost();

        double ratio = context().queryPlanner().tournamentFilterEstimate(tournamentFilter);
        long estimateRows = OperatorCost.capRowEstimate((int) Math.round(sourceCost.estimateRows() * ratio));

        operatorCost
                .estimateRows(estimateRows)
                .estimateDeserializations(estimateRows) // Non-matching rows will mostly be caught non-deserialized
                .estimatePageReads(context().queryPlanner().estimateGamePageReads(sourceCost.estimateRows()));
    }

    @Override
    public String toString() {
        if (tournamentFilter != null) {
            return "TournamentLookup(filter: " + tournamentFilter + ")";
        }
        return "TournamentLookup()";
    }

    @Override
    protected List<MetricsProvider> metricProviders() {
        return List.of(database().tournamentIndex(), database().tournamentExtraStorage());
    }
}

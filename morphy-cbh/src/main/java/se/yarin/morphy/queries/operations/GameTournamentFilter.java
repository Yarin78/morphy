package se.yarin.morphy.queries.operations;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.Game;
import se.yarin.morphy.entities.EntityIndexReadTransaction;
import se.yarin.morphy.entities.Tournament;
import se.yarin.morphy.entities.filters.EntityFilter;
import se.yarin.morphy.metrics.MetricsProvider;
import se.yarin.morphy.queries.QueryContext;

import java.util.List;
import java.util.stream.Stream;

public class GameTournamentFilter extends QueryOperator<Game> {
    private final @NotNull QueryOperator<Game> source;
    private final @NotNull EntityFilter<Tournament> tournamentFilter;

    public GameTournamentFilter(@NotNull QueryContext queryContext, @NotNull QueryOperator<Game> source, @NotNull EntityFilter<Tournament> tournamentFilter) {
        super(queryContext);
        this.source = source;
        this.tournamentFilter = tournamentFilter;
    }

    @Override
    public List<QueryOperator<?>> sources() {
        return List.of(source);
    }

    @Override
    protected Stream<Game> operatorStream() {
        final EntityIndexReadTransaction<Tournament> tournamentTransaction = transaction().tournamentTransaction();

        return this.source.stream().filter(game ->
        {
            Tournament tournament = tournamentTransaction.get(game.tournamentId(), tournamentFilter);
            return tournament != null && tournamentFilter.matches(tournament);
        });
    }

    @Override
    public void estimateOperatorCost(@NotNull ImmutableOperatorCost.Builder operatorCost) {
        OperatorCost sourceCost = source.getOperatorCost();

        double ratio = tournamentFilter.expectedMatch(context().queryPlanner());

        operatorCost
            .estimateRows(OperatorCost.capRowEstimate(sourceCost.estimateRows() * ratio))
            //.estimatePageReads(context().queryPlanner().estimateTournamentPageReads(sourceCost.estimateRows()))
            .estimatePageReads(sourceCost.estimateRows()) // The reads will be scattered
            .estimateDeserializations(OperatorCost.capRowEstimate(sourceCost.estimateRows() * ratio));
    }

    @Override
    public String toString() {
        return "GameTournamentFilter(filter: " + tournamentFilter + ")";
    }

    @Override
    protected List<MetricsProvider> metricProviders() {
        return List.of(database().tournamentIndex(), database().tournamentExtraStorage());
    }
}

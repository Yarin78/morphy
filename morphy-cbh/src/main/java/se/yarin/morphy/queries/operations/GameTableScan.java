package se.yarin.morphy.queries.operations;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.Game;
import se.yarin.morphy.games.filters.GameFilter;
import se.yarin.morphy.queries.QueryContext;

import java.util.List;
import java.util.stream.Stream;

public class GameTableScan extends QueryOperator<Game> {
    private final @Nullable GameFilter gameFilter;

    public GameTableScan(@NotNull QueryContext queryContext, @Nullable GameFilter gameFilter) {
        super(queryContext);
        this.gameFilter = gameFilter;
    }

    @Override
    public List<QueryOperator<?>> sources() {
        return List.of();
    }

    @Override
    public Stream<Game> operatorStream() {
        return transaction().stream(gameFilter);
    }

    @Override
    public OperatorCost estimateCost() {
        int numGames = context().database().count();
        double ratio = context().queryPlanner().gameFilterEstimate(gameFilter);
        long estimateRows = OperatorCost.capRowEstimate((int) Math.round(numGames * ratio));

        long pageReads = context().database().gameHeaderIndex().numDiskPages() +
                context().database().extendedGameHeaderStorage().numDiskPages();

        return ImmutableOperatorCost.builder()
                .rows(estimateRows)
                .numDeserializations(estimateRows) // Non-matching rows will mostly be caught non-deserialized
                .pageReads(pageReads)
                .build();
    }

    @Override
    public String toString() {
        if (gameFilter != null) {
            return "GameTableScan(filter: " + gameFilter + ")";
        }
        return "GameTableScan()";
    }
}

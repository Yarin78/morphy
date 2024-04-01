package se.yarin.morphy.queries;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.Game;
import se.yarin.morphy.entities.Player;
import se.yarin.morphy.entities.filters.EntityFilter;
import se.yarin.morphy.games.filters.GameFilter;
import se.yarin.morphy.queries.operations.MergeJoin;
import se.yarin.morphy.queries.operations.QueryOperator;

import java.util.Collection;
import java.util.List;

class GameSourceQuery implements SourceQuery<Game> {
    private final @Nullable QueryOperator<Game> gameOperator;

    private final boolean optional;
    private final List<GameFilter> filtersCovered;
    private long estimateRows = -1;

    public boolean isOptional() {
        return optional;
    }

    public @NotNull QueryContext context() {
        return gameOperator.context();
    }

    @Override
    public @NotNull List<?> filtersCovered() {
        return filtersCovered;
    }

    private GameSourceQuery(@Nullable QueryOperator<Game> gameOperator,
                            boolean optional,
                            @NotNull List<GameFilter> filtersCovered) {
        this.gameOperator = gameOperator;
        this.optional = optional;
        this.filtersCovered = filtersCovered;
    }


    public static GameSourceQuery fromGameQueryOperator(@NotNull QueryOperator<Game> gameQueryOperator, boolean optional, @NotNull List<GameFilter> filtersCovered) {
        return new GameSourceQuery(gameQueryOperator, optional, filtersCovered);
    }

    public static GameSourceQuery join(@NotNull GameSourceQuery left, @NotNull GameSourceQuery right) {
        List<GameFilter> coveredFilters = (List<GameFilter>) List.copyOf(left.filtersCovered());
        coveredFilters.addAll((Collection<? extends GameFilter>) right.filtersCovered());
        return new GameSourceQuery(new MergeJoin<>(left.context(), left.gameOperator, right.gameOperator), false, coveredFilters);
    }

    public QueryOperator<Game> gameOperator() {
        return gameOperator;
    }

    public long estimateRows() {
        if (estimateRows < 0) {
            estimateRows = gameOperator.getOperatorCost().estimateRows();
        }
        return estimateRows;
    }


}

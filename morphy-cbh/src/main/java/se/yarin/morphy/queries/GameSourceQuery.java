package se.yarin.morphy.queries;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.Game;
import se.yarin.morphy.entities.filters.EntityFilter;
import se.yarin.morphy.queries.operations.MergeJoin;
import se.yarin.morphy.queries.operations.QueryOperator;

class GameSourceQuery implements SourceQuery<Game> {
    private final @Nullable QueryOperator<Game> gameOperator;

    private final @Nullable EntityFilter<?> entityFilter;  // Set if the source matches this filter perfectly

    private final boolean optional;
    private long estimateRows = -1;

    public boolean isOptional() {
        return optional;
    }

    public @NotNull QueryContext context() {
        return gameOperator.context();
    }

    private GameSourceQuery(@Nullable QueryOperator<Game> gameOperator,
                            @Nullable EntityFilter<?> entityFilter,
                            boolean optional) {
        this.gameOperator = gameOperator;
        this.entityFilter = entityFilter; // TODO: Never set?
        this.optional = optional;
    }


    public static GameSourceQuery fromGameQueryOperator(@NotNull QueryOperator<Game> gameQueryOperator, boolean optional) {
        return new GameSourceQuery(gameQueryOperator, null, optional);
    }

    public static GameSourceQuery join(@NotNull GameSourceQuery left, @NotNull GameSourceQuery right) {
        return new GameSourceQuery(new MergeJoin<>(left.context(), left.gameOperator, right.gameOperator), null, false);
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

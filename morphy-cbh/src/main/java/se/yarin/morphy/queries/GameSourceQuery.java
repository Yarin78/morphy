package se.yarin.morphy.queries;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.Game;
import se.yarin.morphy.entities.filters.EntityFilter;
import se.yarin.morphy.queries.operations.HashJoin;
import se.yarin.morphy.queries.operations.MergeGameJoin;
import se.yarin.morphy.queries.operations.QueryOperator;

class GameSourceQuery {
    // Exactly one of these two operators are set
    private final @Nullable QueryOperator<Game> gameOperator;
    private final @Nullable QueryOperator<Integer> gameIdOperator;

    private final @Nullable EntityFilter<?> entityFilter;  // Set if the source matches this filter perfectly

    private boolean optional;
    private long estimateRows = -1;

    public boolean isOptional() {
        return optional;
    }

    public QueryContext context() {
        return gameOperator != null ? gameOperator.context() : gameIdOperator.context();
    }

    private GameSourceQuery(@Nullable QueryOperator<Game> gameOperator,
                            @Nullable QueryOperator<Integer> gameIdOperator,
                            @Nullable EntityFilter<?> entityFilter,
                            boolean optional) {
        this.gameOperator = gameOperator;
        this.gameIdOperator = gameIdOperator;
        this.entityFilter = entityFilter;
        this.optional = optional;
    }

    public static GameSourceQuery fromIdQuery(@NotNull QueryOperator<Integer> gameIdQueryOperator, boolean optional) {
        return new GameSourceQuery(null, gameIdQueryOperator, null, optional);
    }

    public static GameSourceQuery fromGameQuery(@NotNull QueryOperator<Game> gameQueryOperator, boolean optional) {
        return new GameSourceQuery(gameQueryOperator, null, null, optional);
    }

    public static GameSourceQuery join(@NotNull GameSourceQuery left, @NotNull GameSourceQuery right) {
        // TODO: Merge join
        if (left.gameIdOperator != null && right.gameIdOperator != null) {
            return GameSourceQuery.fromIdQuery(new HashJoin(left.context(), left.gameIdOperator, right.gameIdOperator), false);
        } else if (left.gameIdOperator != null && right.gameOperator != null) {
            return GameSourceQuery.fromGameQuery(MergeGameJoin.gameIdJoin(left.context(), right.gameOperator, left.gameIdOperator), false);
        } else if (left.gameOperator != null && right.gameIdOperator != null) {
            return GameSourceQuery.fromGameQuery(MergeGameJoin.gameIdJoin(left.context(), left.gameOperator, right.gameIdOperator), false);
        } else {
            return GameSourceQuery.fromGameQuery(MergeGameJoin.gameGameJoin(left.context(), left.gameOperator, right.gameOperator), false);
        }
    }

    public QueryOperator<Game> gameOperator() {
        return gameOperator;
    }

    public QueryOperator<Integer> gameIdOperator() {
        return gameIdOperator;
    }

    public long estimateRows() {
        if (estimateRows < 0) {
            if (gameOperator != null) {
                estimateRows = gameOperator.getOperatorCost().estimateRows();
            } else {
                assert gameIdOperator != null;
                estimateRows = gameIdOperator.getOperatorCost().estimateRows();
            }
        }
        return estimateRows;
    }


}

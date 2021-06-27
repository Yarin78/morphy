package se.yarin.morphy.queries;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.Game;
import se.yarin.morphy.queries.operations.QueryOperator;

public abstract class GameEntityJoin<T> {
    abstract QueryOperator<Game> applyGameFilter(@NotNull QueryContext context, @NotNull QueryOperator<Game> gameOperator);
}

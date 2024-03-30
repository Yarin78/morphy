package se.yarin.morphy.queries;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.Game;
import se.yarin.morphy.entities.Player;
import se.yarin.morphy.queries.operations.MergeJoin;
import se.yarin.morphy.queries.operations.QueryOperator;

public class PlayerSourceQuery implements SourceQuery<Player> {
    private final @Nullable QueryOperator<Player> playerOperator;

    private final boolean optional;
    private long estimateRows = -1;

    public boolean isOptional() {
        return optional;
    }

    public @NotNull QueryContext context() {
        return playerOperator.context();
    }

    private PlayerSourceQuery(@Nullable QueryOperator<Player> playerOperator,
                            boolean optional) {
        this.playerOperator = playerOperator;
        this.optional = optional;
    }


    public static PlayerSourceQuery fromPlayerQueryOperator(@NotNull QueryOperator<Player> playerQueryOperator, boolean optional) {
        return new PlayerSourceQuery(playerQueryOperator, optional);
    }

    public static PlayerSourceQuery join(@NotNull PlayerSourceQuery left, @NotNull PlayerSourceQuery right) {
        return new PlayerSourceQuery(new MergeJoin<>(left.context(), left.playerOperator, right.playerOperator), false);
    }

    public QueryOperator<Player> playerOperator() {
        return playerOperator;
    }

    public long estimateRows() {
        if (estimateRows < 0) {
            estimateRows = playerOperator.getOperatorCost().estimateRows();
        }
        return estimateRows;
    }
}

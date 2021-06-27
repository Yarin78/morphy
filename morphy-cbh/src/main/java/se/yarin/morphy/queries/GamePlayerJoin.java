package se.yarin.morphy.queries;

import org.jetbrains.annotations.NotNull;

public class GamePlayerJoin {
    private final @NotNull PlayerQuery playerQuery;
    private final @NotNull GamePlayerJoinCondition joinCondition;

    public @NotNull PlayerQuery query() {
        return playerQuery;
    }

    public @NotNull GamePlayerJoinCondition joinCondition() {
        return joinCondition;
    }

    public GamePlayerJoin(@NotNull PlayerQuery playerQuery, @NotNull GamePlayerJoinCondition joinCondition) {
        this.playerQuery = playerQuery;
        this.joinCondition = joinCondition;
    }
}

package se.yarin.morphy.queries;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.Game;
import se.yarin.morphy.entities.Player;
import se.yarin.morphy.entities.filters.CombinedFilter;
import se.yarin.morphy.entities.filters.EntityFilter;
import se.yarin.morphy.queries.operations.GamePlayerFilter;
import se.yarin.morphy.queries.operations.QueryOperator;

public class GamePlayerJoin extends GameEntityJoin<Player> {
    private final @NotNull PlayerQuery playerQuery;
    private final @NotNull GamePlayerJoinCondition joinCondition;

    public @NotNull PlayerQuery query() {
        return playerQuery;
    }

    public boolean isSimpleJoin() {
        return playerQuery.gameQuery() == null;
    }

    public @NotNull GamePlayerJoinCondition joinCondition() {
        return joinCondition;
    }

    public GamePlayerJoin(@NotNull PlayerQuery playerQuery, @NotNull GamePlayerJoinCondition joinCondition) {
        this.playerQuery = playerQuery;
        this.joinCondition = joinCondition;
    }

    @Override
    QueryOperator<Game> applyGameFilter(@NotNull QueryContext context, @NotNull QueryOperator<Game> gameOperator) {
        if (playerQuery.gameQuery() != null) {
            throw new IllegalStateException("Can only apply filter joins at this stage");
        }
        EntityFilter<Player> filter = CombinedFilter.combine(playerQuery.filters());
        return filter == null ? gameOperator : new GamePlayerFilter(context, gameOperator, filter, joinCondition);
    }
}

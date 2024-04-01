package se.yarin.morphy.queries;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.Game;
import se.yarin.morphy.entities.Player;
import se.yarin.morphy.entities.filters.EntityFilter;
import se.yarin.morphy.queries.operations.HashJoin;
import se.yarin.morphy.queries.operations.MergeJoin;
import se.yarin.morphy.queries.operations.QueryOperator;

import java.util.ArrayList;
import java.util.List;

public class PlayerSourceQuery implements SourceQuery<Player> {
    private final @NotNull QueryOperator<Player> playerOperator;

    private final boolean optional;
    private final List<EntityFilter<Player>> filtersCovered;
    private long estimateRows = -1;

    public boolean isOptional() {
        return optional;
    }

    public @NotNull QueryContext context() {
        return playerOperator.context();
    }

    public @NotNull List<EntityFilter<Player>> filtersCovered() {
        return filtersCovered;
    }

    private PlayerSourceQuery(@NotNull QueryOperator<Player> playerOperator,
                            boolean optional,
                            @NotNull List<EntityFilter<Player>> filtersCovered) {
        this.playerOperator = playerOperator;
        this.optional = optional;
        this.filtersCovered = filtersCovered;
    }


    public static PlayerSourceQuery fromPlayerQueryOperator(@NotNull QueryOperator<Player> playerQueryOperator, boolean optional, @NotNull List<EntityFilter<Player>> filtersCovered) {
        return new PlayerSourceQuery(playerQueryOperator, optional, filtersCovered);
    }

    public static PlayerSourceQuery join(@NotNull PlayerSourceQuery left, @NotNull PlayerSourceQuery right) {
        ArrayList<EntityFilter<Player>> coveredFilters = new ArrayList<>();
        coveredFilters.addAll(left.filtersCovered());
        coveredFilters.addAll(right.filtersCovered());
        if (left.playerOperator.sortOrder().isSameOrStronger(QuerySortOrder.byId()) && right.playerOperator.sortOrder().isSameOrStronger(QuerySortOrder.byId())) {
            return new PlayerSourceQuery(new MergeJoin<>(left.context(), left.playerOperator, right.playerOperator), false, coveredFilters);
        }
        return new PlayerSourceQuery(new HashJoin<>(left.context(), left.playerOperator, right.playerOperator), false, coveredFilters);
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

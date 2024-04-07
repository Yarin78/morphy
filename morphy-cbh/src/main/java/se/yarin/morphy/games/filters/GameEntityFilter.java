package se.yarin.morphy.games.filters;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.entities.Entity;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.queries.GameEntityJoinCondition;

import java.util.List;

public interface GameEntityFilter<T extends Entity & Comparable<T>> extends GameFilter {
    @NotNull List<Integer> entityIds();

    @NotNull EntityType entityType();

    @NotNull default GameEntityJoinCondition matchCondition() {
        return GameEntityJoinCondition.ANY;
    }
}

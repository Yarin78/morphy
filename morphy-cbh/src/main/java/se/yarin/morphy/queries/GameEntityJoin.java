package se.yarin.morphy.queries;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.Game;
import se.yarin.morphy.entities.Entity;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.entities.Player;
import se.yarin.morphy.entities.Tournament;
import se.yarin.morphy.entities.filters.CombinedFilter;
import se.yarin.morphy.entities.filters.EntityFilter;
import se.yarin.morphy.queries.joins.GameEntityFilterJoin;
import se.yarin.morphy.queries.joins.GamePlayerFilterJoin;
import se.yarin.morphy.queries.joins.GameTournamentFilterJoin;
import se.yarin.morphy.queries.operations.GameEntityFilter;
import se.yarin.morphy.queries.operations.QueryOperator;

public class GameEntityJoin<T extends Entity & Comparable<T>> {
    private final @NotNull EntityQuery<T> entityQuery;
    private final @NotNull EntityType entityType;
    private final @Nullable GameQueryJoinCondition joinCondition;

    public boolean isSimpleJoin() {
        return entityQuery.gameQuery() == null;
    }

    public @NotNull EntityQuery<T> entityQuery() {
        return entityQuery;
    }

    public @NotNull EntityType getEntityType() {
        return entityType;
    }

    public GameEntityJoin(@NotNull EntityQuery<T> entityQuery, @Nullable GameQueryJoinCondition joinCondition) {
        this.entityQuery = entityQuery;
        this.entityType = entityQuery.entityType();

        if (entityType == EntityType.PLAYER || entityType == EntityType.TEAM) {
            if (joinCondition == null) {
                throw new IllegalArgumentException("Join condition must be specified for player and team joins");
            }
        } else {
            if (joinCondition != null) {
                throw new IllegalArgumentException("Join condition must not be specified for tournament joins");
            }
        }
        this.joinCondition = joinCondition;
    }

    public QueryOperator<Game> applyGameFilter(@NotNull QueryContext context, @NotNull QueryOperator<Game> gameOperator) {
        if (entityQuery().gameQuery() != null) {
            throw new IllegalStateException("Can only apply filter joins at this stage");
        }
        EntityFilter<T> filter = CombinedFilter.combine(entityQuery.filters());
        if (filter == null) {
            return gameOperator;
        }

        // TODO: Make more generic

        GameEntityFilterJoin join = switch (entityType) {
            case PLAYER -> new GamePlayerFilterJoin(joinCondition, (EntityFilter) filter);
            case TOURNAMENT -> new GameTournamentFilterJoin((EntityFilter) filter);
            default -> null;
        };


        return new GameEntityFilter<>(context, gameOperator, entityType, filter, join);
    }
}

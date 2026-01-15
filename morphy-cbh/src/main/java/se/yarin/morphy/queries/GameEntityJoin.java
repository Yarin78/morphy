package se.yarin.morphy.queries;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.Game;
import se.yarin.morphy.entities.Entity;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.entities.filters.CombinedFilter;
import se.yarin.morphy.queries.operations.GameEntityHashJoin;
import se.yarin.morphy.queries.operations.GameEntityLoopJoin;
import se.yarin.morphy.queries.operations.QueryOperator;

public class GameEntityJoin<T extends Entity & Comparable<T>> {
  private final @NotNull EntityQuery<T> entityQuery;
  private final @NotNull EntityType entityType;
  private final @Nullable GameEntityJoinCondition joinCondition;

  public boolean isSimpleJoin() {
    return entityQuery.gameQuery() == null;
  }

  public @NotNull EntityQuery<T> entityQuery() {
    return entityQuery;
  }

  public @NotNull EntityType getEntityType() {
    return entityType;
  }

  public @Nullable GameEntityJoinCondition joinCondition() {
    return joinCondition;
  }

  public GameEntityJoin(
      @NotNull EntityQuery<T> entityQuery, @Nullable GameEntityJoinCondition joinCondition) {
    this.entityQuery = entityQuery;
    this.entityType = entityQuery.entityType();
    this.joinCondition = joinCondition == null ? GameEntityJoinCondition.ANY : joinCondition;

    if (entityType != EntityType.PLAYER
        && entityType != EntityType.TEAM
        && this.joinCondition != GameEntityJoinCondition.ANY) {
      throw new IllegalArgumentException(
          "Join condition can only be specified for joins with player and team entities");
    }
  }

  public QueryOperator<Game> loopJoin(
      QueryContext context, QueryOperator<Game> currentGameOperator) {
    var entityFilter = CombinedFilter.combine(this.entityQuery().filters());
    if (entityFilter == null) {
      return currentGameOperator;
    }
    return new GameEntityLoopJoin<>(
        context, currentGameOperator, getEntityType(), entityFilter, joinCondition());
  }
}

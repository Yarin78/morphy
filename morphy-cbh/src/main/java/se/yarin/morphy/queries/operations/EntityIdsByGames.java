package se.yarin.morphy.queries.operations;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.chess.GameResult;
import se.yarin.morphy.Game;
import se.yarin.morphy.entities.Entity;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.metrics.MetricsProvider;
import se.yarin.morphy.queries.GameEntityJoinCondition;
import se.yarin.morphy.queries.QueryContext;
import se.yarin.morphy.queries.QuerySortOrder;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class EntityIdsByGames<T extends Entity & Comparable<T>> extends QueryOperator<T> {
  private final @NotNull QueryOperator<Game> source;
  private final @NotNull GameEntityJoinCondition joinCondition;
  private final @NotNull EntityType entityType;

  public EntityIdsByGames(
      @NotNull QueryContext queryContext,
      @NotNull EntityType entityType,
      @NotNull QueryOperator<Game> source,
      @Nullable GameEntityJoinCondition joinCondition) {
    super(queryContext, false);
    this.entityType = entityType;
    this.source = source;
    this.joinCondition = joinCondition == null ? GameEntityJoinCondition.ANY : joinCondition;
    assert source.hasFullData();
  }

  @Override
  public List<QueryOperator<?>> sources() {
    return List.of(source);
  }

  public @NotNull QuerySortOrder<T> sortOrder() {
    return QuerySortOrder.none();
  }

  public boolean mayContainDuplicates() {
    return true;
  }

  @Override
  public Stream<QueryData<T>> operatorStream() {
    return source.stream()
        .flatMap(
            row -> {
              int[][] joinIds = joinCondition.getJoinIds(row.data(), entityType);
              return Arrays.stream(joinIds)
                  .flatMap(ids -> Arrays.stream(ids).boxed().filter(id -> id >= 0))
                  .map(QueryData<T>::new);
            });
  }

  @Override
  public void estimateOperatorCost(@NotNull ImmutableOperatorCost.Builder operatorCost) {
    OperatorCost sourceCost = source.getOperatorCost();

    int entityCount = context().entityIndex(entityType).count();
    int gameCount = Math.max(1, context().database().count());

    operatorCost
        .estimateRows(
            OperatorCost.capRowEstimate(entityCount * sourceCost.estimateRows() / gameCount))
        .estimateDeserializations(0)
        .estimatePageReads(0)
        .build();
  }

  @Override
  public String toString() {
    return entityType.nameSingularCapitalized() + "IdsByGames()";
  }
}

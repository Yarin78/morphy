package se.yarin.morphy.queries.operations;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.chess.GameResult;
import se.yarin.morphy.Game;
import se.yarin.morphy.entities.Entity;
import se.yarin.morphy.entities.EntityIndexReadTransaction;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.entities.filters.EntityFilter;
import se.yarin.morphy.metrics.MetricsProvider;
import se.yarin.morphy.queries.GameEntityJoinCondition;
import se.yarin.morphy.queries.QueryContext;
import se.yarin.morphy.queries.QuerySortOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class GameEntityLoopJoin<T extends Entity & Comparable<T>> extends QueryOperator<Game> {
  private final @NotNull QueryOperator<Game> source;
  private final @NotNull EntityFilter<T> entityFilter;
  private final @NotNull EntityType entityType;
  private final @NotNull GameEntityJoinCondition joinCondition;

  public GameEntityLoopJoin(
      @NotNull QueryContext queryContext,
      @NotNull QueryOperator<Game> source,
      @NotNull EntityType entityType,
      @NotNull EntityFilter<T> entityFilter,
      @Nullable GameEntityJoinCondition joinCondition) {
    super(queryContext, true);
    if (!source.hasFullData()) {
      throw new IllegalArgumentException("The source of GameEntityFilter must return full data");
    }
    this.source = source;
    this.entityType = entityType;
    this.entityFilter = entityFilter;
    this.joinCondition = joinCondition != null ? joinCondition : GameEntityJoinCondition.ANY;
  }

  @Override
  public List<QueryOperator<?>> sources() {
    return List.of(source);
  }

  public @NotNull QuerySortOrder<Game> sortOrder() {
    return source.sortOrder();
  }

  public boolean mayContainDuplicates() {
    return source.mayContainDuplicates();
  }

  @Override
  public Stream<QueryData<Game>> operatorStream() {
    final EntityIndexReadTransaction<T> entityTransaction =
        (EntityIndexReadTransaction<T>) transaction().entityTransaction(entityType);

    return this.source.stream()
        .filter(
            game -> {
              Game gameData = game.data();
              int[][] joinIdGroups = joinCondition.getJoinIds(gameData, entityType);
              for (int[] joinIds : joinIdGroups) {
                int matchCnt = 0;
                for (int joinId : joinIds) {
                  T entity = joinId < 0 ? null : entityTransaction.get(joinId, this.entityFilter);
                  if (entity != null) {
                    matchCnt++;
                  }
                }
                if (matchCnt == joinIds.length) {
                  return true;
                }
              }
              return false;
            });
  }

  @Override
  public void estimateOperatorCost(@NotNull ImmutableOperatorCost.Builder operatorCost) {
    OperatorCost sourceCost = source.getOperatorCost();

    double ratio = context().queryPlanner().entityFilterEstimate(entityFilter, entityType);
    // TODO: Make more generic
    double anyRatio =
        1 - (1 - ratio) * (1 - ratio); // ratio than any of the two players matches the filter

    operatorCost
        .estimateRows(OperatorCost.capRowEstimate(sourceCost.estimateRows() * anyRatio))
        // .estimatePageReads(context().queryPlanner().estimatePlayerPageReads(sourceCost.estimateRows()))
        .estimatePageReads(
            sourceCost
                .estimateRows()) // The reads will be scattered; TODO: But if very many read, the
        // disk cache will work nice anyhow
        .estimateDeserializations(
            2 * OperatorCost.capRowEstimate(sourceCost.estimateRows() * anyRatio))
        .build();
  }

  @Override
  public String toString() {
    ArrayList<String> params = new ArrayList<>();
    params.add("filter: " + entityFilter);
    if (entityType == EntityType.PLAYER || entityType == EntityType.TEAM) {
      params.add("join: " + joinCondition);
    }

    return "Game"
        + entityType.nameSingularCapitalized()
        + "LoopJoin("
        + String.join(", ", params)
        + ")";
  }
}

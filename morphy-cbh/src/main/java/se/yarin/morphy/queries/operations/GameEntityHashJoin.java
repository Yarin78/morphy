package se.yarin.morphy.queries.operations;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.Game;
import se.yarin.morphy.IdObject;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.metrics.MetricsProvider;
import se.yarin.morphy.queries.GameEntityJoinCondition;
import se.yarin.morphy.queries.QueryContext;
import se.yarin.morphy.queries.QuerySortOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GameEntityHashJoin extends QueryOperator<Game> {
  private final @NotNull QueryOperator<Game> source;
  private final @NotNull QueryOperator<?> entitySource;
  private final @NotNull EntityType entityType;
  private final @NotNull GameEntityJoinCondition joinCondition;

  public GameEntityHashJoin(
      @NotNull QueryContext queryContext,
      @NotNull QueryOperator<Game> source,
      @NotNull EntityType entityType,
      @NotNull QueryOperator<?> entitySource,
      @Nullable GameEntityJoinCondition joinCondition) {
    super(queryContext, true);
    if (!source.hasFullData()) {
      throw new IllegalArgumentException("The source of GameEntityFilter must return full data");
    }
    this.source = source;
    this.entityType = entityType;
    this.entitySource = entitySource;
    this.joinCondition = joinCondition != null ? joinCondition : GameEntityJoinCondition.ANY;
  }

  @Override
  public List<QueryOperator<?>> sources() {
    return List.of(source, entitySource);
  }

  public @NotNull QuerySortOrder<Game> sortOrder() {
    return source.sortOrder();
  }

  public boolean mayContainDuplicates() {
    return source.mayContainDuplicates();
  }

  @Override
  public Stream<QueryData<Game>> operatorStream() {
    Set<Integer> entityIds =
        entitySource.stream()
            .map(IdObject::id)
            .collect(Collectors.toSet());

    return this.source.stream()
        .map(
            game -> {
              Game gameData = game.data();
              int[][] joinIdGroups = joinCondition.getJoinIds(gameData, entityType);
              for (int[] joinIds : joinIdGroups) {
                boolean allMatch = true;
                for (int joinId : joinIds) {
                  if (!entityIds.contains(joinId)) {
                    allMatch = false;
                    break;
                  }
                }
                if (allMatch) {
                  return new QueryData<>(game.id(), gameData, game.extra());
                }
              }
              return null;
            })
        .filter(Objects::nonNull);
  }

  @Override
  public void estimateOperatorCost(@NotNull ImmutableOperatorCost.Builder operatorCost) {
    // TODO
  }

  @Override
  public String toString() {
    ArrayList<String> params = new ArrayList<>();
    if (entityType == EntityType.PLAYER || entityType == EntityType.TEAM) {
      params.add("join: " + joinCondition);
    }

    return "Game"
        + entityType.nameSingularCapitalized()
        + "HashJoin("
        + String.join(", ", params)
        + ")";
  }
}

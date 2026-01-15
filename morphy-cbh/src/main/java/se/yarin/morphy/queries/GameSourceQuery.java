package se.yarin.morphy.queries;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.Game;
import se.yarin.morphy.entities.Player;
import se.yarin.morphy.entities.filters.EntityFilter;
import se.yarin.morphy.games.filters.GameFilter;
import se.yarin.morphy.queries.operations.MergeJoin;
import se.yarin.morphy.queries.operations.QueryOperator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

class GameSourceQuery implements SourceQuery<Game> {
  private final @NotNull QueryOperator<Game> gameOperator;

  private final boolean optional;
  private final List<GameFilter> filtersCovered;
  private final List<GameEntityJoin<?>> entityJoinsCovered;
  private long estimateRows = -1;

  public boolean isOptional() {
    return optional;
  }

  public @NotNull QueryContext context() {
    return gameOperator.context();
  }

  @Override
  public @NotNull List<GameFilter> filtersCovered() {
    return filtersCovered;
  }

  public @NotNull List<GameEntityJoin<?>> entityJoinsCovered() {
    return entityJoinsCovered;
  }

  private GameSourceQuery(
      @NotNull QueryOperator<Game> gameOperator,
      boolean optional,
      @NotNull List<GameFilter> filtersCovered,
      @NotNull List<GameEntityJoin<?>> entityJoinsCovered) {
    this.gameOperator = gameOperator;
    this.optional = optional;
    this.filtersCovered = filtersCovered;
    this.entityJoinsCovered = entityJoinsCovered;
  }

  public static GameSourceQuery fromGameQueryOperator(
      @NotNull QueryOperator<Game> gameQueryOperator,
      boolean optional,
      @NotNull List<GameFilter> filtersCovered,
      @NotNull List<GameEntityJoin<?>> entityJoinsCovered) {
    return new GameSourceQuery(gameQueryOperator, optional, filtersCovered, entityJoinsCovered);
  }

  public static GameSourceQuery join(
      @NotNull GameSourceQuery left, @NotNull GameSourceQuery right) {
    ArrayList<GameFilter> coveredFilters = new ArrayList<>();
    ArrayList<GameEntityJoin<?>> coveredEntityJoins = new ArrayList<>();
    coveredFilters.addAll(left.filtersCovered());
    coveredFilters.addAll(right.filtersCovered());
    coveredEntityJoins.addAll(left.entityJoinsCovered());
    coveredEntityJoins.addAll(right.entityJoinsCovered());

    return new GameSourceQuery(
        new MergeJoin<>(left.context(), left.gameOperator, right.gameOperator),
        false,
        coveredFilters,
        coveredEntityJoins);
  }

  public QueryOperator<Game> gameOperator() {
    return gameOperator;
  }

  public long estimateRows() {
    if (estimateRows < 0) {
      estimateRows = gameOperator.getOperatorCost().estimateRows();
    }
    return estimateRows;
  }
}

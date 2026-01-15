package se.yarin.morphy.queries;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.IdObject;
import se.yarin.morphy.entities.filters.EntityFilter;
import se.yarin.morphy.queries.operations.HashJoin;
import se.yarin.morphy.queries.operations.MergeJoin;
import se.yarin.morphy.queries.operations.QueryOperator;

import java.util.ArrayList;
import java.util.List;

public class EntitySourceQuery<T extends IdObject> implements SourceQuery<T> {
  private final @NotNull QueryOperator<T> operator;

  private final boolean optional;
  private final List<EntityFilter<T>> filtersCovered;
  private long estimateRows = -1;

  public boolean isOptional() {
    return optional;
  }

  public @NotNull QueryContext context() {
    return operator.context();
  }

  public @NotNull List<EntityFilter<T>> filtersCovered() {
    return filtersCovered;
  }

  private EntitySourceQuery(
      @NotNull QueryOperator<T> operator,
      boolean optional,
      @NotNull List<EntityFilter<T>> filtersCovered) {
    this.operator = operator;
    this.optional = optional;
    this.filtersCovered = filtersCovered;
  }

  public static <T extends IdObject> EntitySourceQuery<T> fromQueryOperator(
      @NotNull QueryOperator<T> playerQueryOperator,
      boolean optional,
      @NotNull List<EntityFilter<T>> filtersCovered) {
    return new EntitySourceQuery<>(playerQueryOperator, optional, filtersCovered);
  }

  public static <T extends IdObject> EntitySourceQuery<T> join(
      @NotNull EntitySourceQuery<T> left, @NotNull EntitySourceQuery<T> right) {
    ArrayList<EntityFilter<T>> coveredFilters = new ArrayList<>();
    coveredFilters.addAll(left.filtersCovered());
    coveredFilters.addAll(right.filtersCovered());
    if (left.operator.sortOrder().isSameOrStronger(QuerySortOrder.byId())
        && right.operator.sortOrder().isSameOrStronger(QuerySortOrder.byId())) {
      return new EntitySourceQuery<T>(
          new MergeJoin<>(left.context(), left.operator, right.operator), false, coveredFilters);
    }
    return new EntitySourceQuery<T>(
        new HashJoin<>(left.context(), left.operator, right.operator), false, coveredFilters);
  }

  public QueryOperator<T> operator() {
    return operator;
  }

  public long estimateRows() {
    if (estimateRows < 0) {
      estimateRows = operator.getOperatorCost().estimateRows();
    }
    return estimateRows;
  }
}

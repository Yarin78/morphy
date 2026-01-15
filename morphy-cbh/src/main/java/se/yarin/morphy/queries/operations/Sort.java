package se.yarin.morphy.queries.operations;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.IdObject;
import se.yarin.morphy.metrics.MetricsProvider;
import se.yarin.morphy.queries.QueryContext;
import se.yarin.morphy.queries.QuerySortOrder;

import java.util.List;
import java.util.stream.Stream;

public class Sort<T extends IdObject> extends QueryOperator<T> {
  private final @NotNull QueryOperator<T> source;
  private final @NotNull QuerySortOrder<T> sortOrder;

  public Sort(@NotNull QueryContext queryContext, @NotNull QueryOperator<T> source) {
    this(queryContext, source, QuerySortOrder.byId());
  }

  public Sort(
      @NotNull QueryContext queryContext,
      @NotNull QueryOperator<T> source,
      @NotNull QuerySortOrder<T> sortOrder) {
    super(queryContext, source.hasFullData());
    this.source = source;
    this.sortOrder = sortOrder;
  }

  @Override
  public List<QueryOperator<?>> sources() {
    return List.of(source);
  }

  public @NotNull QuerySortOrder<T> sortOrder() {
    return sortOrder;
  }

  public boolean mayContainDuplicates() {
    return source.mayContainDuplicates();
  }

  @Override
  public Stream<QueryData<T>> operatorStream() {
    return source.stream().sorted(sortOrder);
  }

  @Override
  public void estimateOperatorCost(@NotNull ImmutableOperatorCost.Builder operatorCost) {
    operatorCost
        .estimateRows(source.getOperatorCost().estimateRows())
        .estimateDeserializations(0)
        .estimatePageReads(0);
  }

  @Override
  public String toString() {
    return "Sort(sortOrder=" + sortOrder + ")";
  }
}

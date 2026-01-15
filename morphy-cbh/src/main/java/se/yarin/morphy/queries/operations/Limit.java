package se.yarin.morphy.queries.operations;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.IdObject;
import se.yarin.morphy.metrics.MetricsProvider;
import se.yarin.morphy.queries.QueryContext;
import se.yarin.morphy.queries.QuerySortOrder;

import java.util.List;
import java.util.stream.Stream;

public class Limit<T extends IdObject> extends QueryOperator<T> {
  private final @NotNull QueryOperator<T> source;

  private final int limit; // 0 = all

  public Limit(@NotNull QueryContext queryContext, @NotNull QueryOperator<T> source, int limit) {
    super(queryContext, source.hasFullData());
    this.source = source;
    this.limit = limit;
  }

  @Override
  public List<QueryOperator<?>> sources() {
    return List.of(source);
  }

  public @NotNull QuerySortOrder<T> sortOrder() {
    return this.source.sortOrder();
  }

  public boolean mayContainDuplicates() {
    return source.mayContainDuplicates();
  }

  public Stream<QueryData<T>> operatorStream() {
    return limit > 0 ? source.stream().limit(limit) : source.stream();
  }

  @Override
  public void estimateOperatorCost(@NotNull ImmutableOperatorCost.Builder operatorCost) {
    operatorCost
        .estimateRows(limit > 0 ? limit : source.getOperatorCost().estimateRows())
        .estimateDeserializations(0)
        .estimatePageReads(0);
  }

  @Override
  public String toString() {
    return "Limit(limit=" + limit + ")";
  }
}

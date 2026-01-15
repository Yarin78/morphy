package se.yarin.morphy.queries.operations;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.IdObject;
import se.yarin.morphy.entities.Annotator;
import se.yarin.morphy.metrics.MetricsProvider;
import se.yarin.morphy.queries.QueryContext;
import se.yarin.morphy.queries.QuerySortOrder;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class Distinct<T extends IdObject> extends QueryOperator<T> {
  private final @NotNull QueryOperator<T> source;

  public Distinct(@NotNull QueryContext queryContext, @NotNull QueryOperator<T> source) {
    super(queryContext, source.hasFullData());
    this.source = source;
  }

  @Override
  public List<QueryOperator<?>> sources() {
    return List.of(source);
  }

  public @NotNull QuerySortOrder<T> sortOrder() {
    return source.sortOrder();
  }

  public boolean mayContainDuplicates() {
    return false;
  }

  public Stream<QueryData<T>> operatorStream() {
    if (source.sortOrder().isSameOrStronger(QuerySortOrder.byId())) {
      var lastId = new AtomicInteger(-1);
      return source.stream()
          .filter(
              data -> {
                if (data.id() == lastId.get()) {
                  return false;
                }
                assert data.id() > lastId.get();
                lastId.set(data.id());
                return true;
              });

    } else {
      HashSet<Integer> seen = new HashSet<>();
      return source.stream().filter(data -> seen.add(data.id()));
    }
  }

  @Override
  public void estimateOperatorCost(@NotNull ImmutableOperatorCost.Builder operatorCost) {
    OperatorCost sourceCost = source.getOperatorCost();
    operatorCost
        .estimateRows(sourceCost.estimateRows())
        .estimateDeserializations(0)
        .estimatePageReads(0)
        .build();
  }

  @Override
  public String toString() {
    return "Distinct()";
  }
}

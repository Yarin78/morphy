package se.yarin.morphy.query;

import java.util.List;
import java.util.Objects;
import java.util.function.IntFunction;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Lookup<T> extends QueryNode<T> {
  private final @NotNull QueryNode<?> source;
  private final @NotNull IntFunction<@Nullable T> fetcher;
  private final @Nullable DataFilter<T> filter;
  private final @NotNull SortOrder<T> outputSortOrder;

  public Lookup(
      @NotNull QueryNode<?> source,
      @NotNull IntFunction<@Nullable T> fetcher,
      @Nullable DataFilter<T> filter,
      @NotNull SortOrder<T> outputSortOrder) {
    this.source = source;
    this.fetcher = fetcher;
    this.filter = filter;
    this.outputSortOrder = outputSortOrder;
  }

  public Lookup(@NotNull QueryNode<?> source, @NotNull IntFunction<@Nullable T> fetcher) {
    this(source, fetcher, null, source.sortOrder().isNone() ? SortOrder.none() : SortOrder.byId());
  }

  public Lookup(
      @NotNull QueryNode<?> source,
      @NotNull IntFunction<@Nullable T> fetcher,
      @Nullable DataFilter<T> filter) {
    this(source, fetcher, filter, source.sortOrder().isNone() ? SortOrder.none() : SortOrder.byId());
  }

  @Override
  public @NotNull List<QueryNode<?>> sources() {
    return List.of(source);
  }

  @Override
  public @NotNull SortOrder<T> sortOrder() {
    return outputSortOrder;
  }

  @Override
  public boolean mayContainDuplicates() {
    return source.mayContainDuplicates();
  }

  @Override
  public @NotNull Stream<QueryData<T>> stream() {
    return source.stream()
        .map(
            row -> {
              T data = fetcher.apply(row.id());
              if (data == null) return null;
              return new QueryData<>(row.id(), data);
            })
        .filter(Objects::nonNull)
        .filter(
            qd -> {
              if (filter == null) return true;
              assert qd.data() != null;
              return filter.matches(qd.data());
            });
  }

  @Override
  public String toString() {
    return "Lookup[" + (filter != null ? "filtered" : "") + "]";
  }
}

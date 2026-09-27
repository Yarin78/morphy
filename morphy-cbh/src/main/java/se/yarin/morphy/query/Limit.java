package se.yarin.morphy.query;

import java.util.List;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;

public class Limit<T> extends QueryNode<T> {
  private final @NotNull QueryNode<T> source;
  private final int limit;

  public Limit(@NotNull QueryNode<T> source, int limit) {
    if (limit <= 0) {
      throw new IllegalArgumentException("Limit must be positive: " + limit);
    }
    this.source = source;
    this.limit = limit;
  }

  @Override
  public @NotNull List<QueryNode<?>> sources() {
    return List.of(source);
  }

  @Override
  public @NotNull SortOrder<T> sortOrder() {
    return source.sortOrder();
  }

  @Override
  public boolean mayContainDuplicates() {
    return source.mayContainDuplicates();
  }

  @Override
  public @NotNull Stream<QueryData<T>> stream() {
    return source.stream().limit(limit);
  }

  @Override
  public String toString() {
    return "Limit[" + limit + "]";
  }
}

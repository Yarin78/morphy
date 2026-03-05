package se.yarin.morphy.query;

import java.util.List;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;

public class Sort<T> extends QueryNode<T> {
  private final @NotNull QueryNode<T> source;
  private final @NotNull SortOrder<T> targetSortOrder;

  public Sort(@NotNull QueryNode<T> source, @NotNull SortOrder<T> targetSortOrder) {
    this.source = source;
    this.targetSortOrder = targetSortOrder;
  }

  @Override
  public @NotNull List<QueryNode<?>> sources() {
    return List.of(source);
  }

  @Override
  public @NotNull SortOrder<T> sortOrder() {
    return targetSortOrder;
  }

  @Override
  public boolean mayContainDuplicates() {
    return source.mayContainDuplicates();
  }

  @Override
  public @NotNull Stream<QueryData<T>> stream() {
    return source.stream().sorted(targetSortOrder);
  }

  @Override
  public String toString() {
    return "Sort[" + targetSortOrder + "]";
  }
}

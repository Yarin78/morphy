package se.yarin.morphy.query;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;

public class FilterNode<T> extends QueryNode<T> {
  private final @NotNull QueryNode<T> source;
  private final @NotNull Predicate<QueryData<T>> predicate;

  public FilterNode(
      @NotNull QueryNode<T> source, @NotNull Predicate<QueryData<T>> predicate) {
    this.source = source;
    this.predicate = predicate;
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
    return source.stream().filter(predicate);
  }

  @Override
  public String toString() {
    return "Filter";
  }
}

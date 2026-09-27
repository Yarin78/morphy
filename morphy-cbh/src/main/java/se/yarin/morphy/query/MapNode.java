package se.yarin.morphy.query;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;

public class MapNode<I, O> extends QueryNode<O> {
  private final @NotNull QueryNode<I> source;
  private final @NotNull Function<QueryData<I>, QueryData<O>> mapper;
  private final boolean preservesSortOrder;

  public MapNode(
      @NotNull QueryNode<I> source,
      @NotNull Function<QueryData<I>, QueryData<O>> mapper,
      boolean preservesSortOrder) {
    this.source = source;
    this.mapper = mapper;
    this.preservesSortOrder = preservesSortOrder;
  }

  @Override
  public @NotNull List<QueryNode<?>> sources() {
    return List.of(source);
  }

  @SuppressWarnings("unchecked")
  @Override
  public @NotNull SortOrder<O> sortOrder() {
    if (preservesSortOrder) {
      // Safe cast: preservesSortOrder should only be true when I and O are the same type,
      // or when the sort order is compatible.
      return (SortOrder<O>) (SortOrder<?>) source.sortOrder();
    }
    return SortOrder.none();
  }

  @Override
  public boolean mayContainDuplicates() {
    return source.mayContainDuplicates();
  }

  @Override
  public @NotNull Stream<QueryData<O>> stream() {
    return source.stream().map(mapper);
  }

  @Override
  public String toString() {
    return "Map[" + (preservesSortOrder ? "order-preserving" : "") + "]";
  }
}

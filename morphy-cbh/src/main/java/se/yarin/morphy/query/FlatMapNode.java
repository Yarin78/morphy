package se.yarin.morphy.query;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;

public class FlatMapNode<I, O> extends QueryNode<O> {
  private final @NotNull QueryNode<I> source;
  private final @NotNull Function<QueryData<I>, List<QueryData<O>>> mapper;

  public FlatMapNode(
      @NotNull QueryNode<I> source,
      @NotNull Function<QueryData<I>, List<QueryData<O>>> mapper) {
    this.source = source;
    this.mapper = mapper;
  }

  @Override
  public @NotNull List<QueryNode<?>> sources() {
    return List.of(source);
  }

  @Override
  public @NotNull SortOrder<O> sortOrder() {
    return SortOrder.none();
  }

  @Override
  public boolean mayContainDuplicates() {
    return true;
  }

  @Override
  public @NotNull Stream<QueryData<O>> stream() {
    return source.stream().flatMap(data -> mapper.apply(data).stream());
  }

  @Override
  public String toString() {
    return "FlatMap[]";
  }
}

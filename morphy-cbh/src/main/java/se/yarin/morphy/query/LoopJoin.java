package se.yarin.morphy.query;

import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LoopJoin<L, R> extends QueryNode<L> {
  private final @NotNull QueryNode<L> outer;
  private final @NotNull ToIntFunction<QueryData<L>> joinKeyExtractor;
  private final @NotNull IntFunction<Stream<QueryData<R>>> innerSourceFactory;
  private final @NotNull BiFunction<QueryData<L>, QueryData<R>, @Nullable QueryData<L>>
      resultMapper;

  public LoopJoin(
      @NotNull QueryNode<L> outer,
      @NotNull ToIntFunction<QueryData<L>> joinKeyExtractor,
      @NotNull IntFunction<Stream<QueryData<R>>> innerSourceFactory,
      @NotNull BiFunction<QueryData<L>, QueryData<R>, @Nullable QueryData<L>> resultMapper) {
    this.outer = outer;
    this.joinKeyExtractor = joinKeyExtractor;
    this.innerSourceFactory = innerSourceFactory;
    this.resultMapper = resultMapper;
  }

  @Override
  public @NotNull List<QueryNode<?>> sources() {
    return List.of(outer);
  }

  @Override
  public @NotNull SortOrder<L> sortOrder() {
    return SortOrder.none();
  }

  @Override
  public boolean mayContainDuplicates() {
    return true;
  }

  @Override
  public @NotNull Stream<QueryData<L>> stream() {
    return outer.stream()
        .flatMap(
            outerRow -> {
              int key = joinKeyExtractor.applyAsInt(outerRow);
              return innerSourceFactory.apply(key).map(innerRow -> resultMapper.apply(outerRow, innerRow));
            })
        .filter(Objects::nonNull);
  }

  @Override
  public String toString() {
    return "LoopJoin";
  }
}

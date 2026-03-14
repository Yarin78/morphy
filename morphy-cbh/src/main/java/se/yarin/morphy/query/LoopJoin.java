package se.yarin.morphy.query;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.ToIntFunction;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LoopJoin<L, R> extends QueryNode<L> {
  private final @NotNull QueryNode<L> left;
  private final @NotNull QueryNode<R> right;
  private final @NotNull JoinType joinType;
  private final @NotNull ToIntFunction<QueryData<L>> leftKey;
  private final @NotNull ToIntFunction<QueryData<R>> rightKey;
  private final @Nullable BiFunction<QueryData<L>, QueryData<R>, QueryData<L>> combiner;

  public LoopJoin(
      @NotNull QueryNode<L> left,
      @NotNull QueryNode<R> right,
      @NotNull JoinType joinType,
      @NotNull ToIntFunction<QueryData<L>> leftKey,
      @NotNull ToIntFunction<QueryData<R>> rightKey,
      @Nullable BiFunction<QueryData<L>, QueryData<R>, QueryData<L>> combiner) {
    if (joinType == JoinType.INNER && combiner == null) {
      throw new IllegalArgumentException("Combiner is required for INNER join");
    }
    this.left = left;
    this.right = right;
    this.joinType = joinType;
    this.leftKey = leftKey;
    this.rightKey = rightKey;
    this.combiner = combiner;
  }

  public static <L, R> LoopJoin<L, R> inner(
      @NotNull QueryNode<L> left,
      @NotNull QueryNode<R> right,
      @NotNull ToIntFunction<QueryData<L>> leftKey,
      @NotNull ToIntFunction<QueryData<R>> rightKey,
      @NotNull BiFunction<QueryData<L>, QueryData<R>, QueryData<L>> combiner) {
    return new LoopJoin<>(left, right, JoinType.INNER, leftKey, rightKey, combiner);
  }

  public static <T> LoopJoin<T, T> inner(
      @NotNull QueryNode<T> left, @NotNull QueryNode<T> right) {
    return new LoopJoin<>(
        left, right, JoinType.INNER, QueryData::id, QueryData::id, QueryData.merger());
  }

  public static <L, R> LoopJoin<L, R> semi(
      @NotNull QueryNode<L> left,
      @NotNull QueryNode<R> right,
      @NotNull ToIntFunction<QueryData<L>> leftKey,
      @NotNull ToIntFunction<QueryData<R>> rightKey) {
    return new LoopJoin<>(left, right, JoinType.SEMI, leftKey, rightKey, null);
  }

  public static <T> LoopJoin<T, T> semi(
      @NotNull QueryNode<T> left, @NotNull QueryNode<T> right) {
    return new LoopJoin<>(left, right, JoinType.SEMI, QueryData::id, QueryData::id, null);
  }

  public static <L, R> LoopJoin<L, R> anti(
      @NotNull QueryNode<L> left,
      @NotNull QueryNode<R> right,
      @NotNull ToIntFunction<QueryData<L>> leftKey,
      @NotNull ToIntFunction<QueryData<R>> rightKey) {
    return new LoopJoin<>(left, right, JoinType.ANTI, leftKey, rightKey, null);
  }

  public static <T> LoopJoin<T, T> anti(
      @NotNull QueryNode<T> left, @NotNull QueryNode<T> right) {
    return new LoopJoin<>(left, right, JoinType.ANTI, QueryData::id, QueryData::id, null);
  }

  public @NotNull JoinType joinType() {
    return joinType;
  }

  @Override
  public @NotNull List<QueryNode<?>> sources() {
    return List.of(left, right);
  }

  @Override
  public @NotNull SortOrder<L> sortOrder() {
    return switch (joinType) {
      case INNER -> SortOrder.none();
      case SEMI, ANTI -> left.sortOrder();
    };
  }

  @Override
  public boolean mayContainDuplicates() {
    return switch (joinType) {
      case INNER -> true;
      case SEMI, ANTI -> left.mayContainDuplicates();
    };
  }

  @SuppressWarnings("unchecked")
  private @NotNull Stream<QueryData<R>> lookupRight(int key) {
    if (right instanceof IndexScanNode<?, ?> scanNode) {
      QueryData<R> result = ((IndexScanNode<R, Integer>) scanNode).getByKey(key);
      return result != null ? Stream.of(result) : Stream.empty();
    }
    return right.stream().filter(qd -> rightKey.applyAsInt(qd) == key);
  }

  @Override
  public @NotNull Stream<QueryData<L>> stream() {
    return switch (joinType) {
      case INNER ->
          left.stream()
              .flatMap(
                  leftRow -> {
                    int key = leftKey.applyAsInt(leftRow);
                    return lookupRight(key)
                        .map(rightRow -> combiner.apply(leftRow, rightRow));
                  });
      case SEMI ->
          left.stream()
              .filter(leftRow -> lookupRight(leftKey.applyAsInt(leftRow)).findAny().isPresent());
      case ANTI ->
          left.stream()
              .filter(leftRow -> lookupRight(leftKey.applyAsInt(leftRow)).findAny().isEmpty());
    };
  }

  @Override
  public String toString() {
    return "LoopJoin[" + joinType + "]";
  }
}

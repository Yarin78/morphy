package se.yarin.morphy.query;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;

public class HashJoin<L, R> extends QueryNode<L> {
  private final @NotNull QueryNode<L> left;
  private final @NotNull QueryNode<R> right;
  private final @NotNull JoinType joinType;
  private final @NotNull ToIntFunction<QueryData<L>> leftKey;
  private final @NotNull ToIntFunction<QueryData<R>> rightKey;
  private final boolean sameType;

  private HashJoin(
      @NotNull QueryNode<L> left,
      @NotNull QueryNode<R> right,
      @NotNull JoinType joinType,
      @NotNull ToIntFunction<QueryData<L>> leftKey,
      @NotNull ToIntFunction<QueryData<R>> rightKey,
      boolean sameType) {
    if (right.mayContainDuplicates()) {
      throw new IllegalArgumentException("Right source must not contain duplicates");
    }
    this.left = left;
    this.right = right;
    this.joinType = joinType;
    this.leftKey = leftKey;
    this.rightKey = rightKey;
    this.sameType = sameType;
  }

  public static <T> HashJoin<T, T> inner(
      @NotNull QueryNode<T> left, @NotNull QueryNode<T> right) {
    return new HashJoin<>(left, right, JoinType.INNER, QueryData::id, QueryData::id, true);
  }

  public static <L, R> HashJoin<L, R> semi(
      @NotNull QueryNode<L> left,
      @NotNull QueryNode<R> right,
      @NotNull ToIntFunction<QueryData<L>> leftKey,
      @NotNull ToIntFunction<QueryData<R>> rightKey) {
    return new HashJoin<>(left, right, JoinType.SEMI, leftKey, rightKey, false);
  }

  public static <T> HashJoin<T, T> semi(
      @NotNull QueryNode<T> left, @NotNull QueryNode<T> right) {
    return new HashJoin<>(left, right, JoinType.SEMI, QueryData::id, QueryData::id, true);
  }

  public static <L, R> HashJoin<L, R> anti(
      @NotNull QueryNode<L> left,
      @NotNull QueryNode<R> right,
      @NotNull ToIntFunction<QueryData<L>> leftKey,
      @NotNull ToIntFunction<QueryData<R>> rightKey) {
    return new HashJoin<>(left, right, JoinType.ANTI, leftKey, rightKey, false);
  }

  public static <T> HashJoin<T, T> anti(
      @NotNull QueryNode<T> left, @NotNull QueryNode<T> right) {
    return new HashJoin<>(left, right, JoinType.ANTI, QueryData::id, QueryData::id, true);
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
    return left.sortOrder();
  }

  @Override
  public boolean mayContainDuplicates() {
    return left.mayContainDuplicates();
  }

  @Override
  public @NotNull Stream<QueryData<L>> stream() {
    Map<Integer, QueryData<R>> hashMap = new HashMap<>();
    right.stream().forEach(qd -> hashMap.put(rightKey.applyAsInt(qd), qd));

    return switch (joinType) {
      case INNER ->
          left.stream()
              .filter(qd -> hashMap.containsKey(leftKey.applyAsInt(qd)))
              .map(qd -> QueryData.combine(qd, hashMap.get(leftKey.applyAsInt(qd)), sameType));
      case SEMI ->
          left.stream()
              .filter(qd -> hashMap.containsKey(leftKey.applyAsInt(qd)))
              .map(qd -> QueryData.combine(qd, hashMap.get(leftKey.applyAsInt(qd)), sameType));
      case ANTI -> left.stream().filter(qd -> !hashMap.containsKey(leftKey.applyAsInt(qd)));
    };
  }

  @Override
  public String toString() {
    return "HashJoin[" + joinType + "]";
  }
}

package se.yarin.morphy.query;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;

public class HashJoin<T> extends QueryNode<T> {
  private final @NotNull QueryNode<T> left;
  private final @NotNull QueryNode<T> right;

  public HashJoin(@NotNull QueryNode<T> left, @NotNull QueryNode<T> right) {
    if (left.mayContainDuplicates()) {
      throw new IllegalArgumentException("Left source must not contain duplicates");
    }
    if (right.mayContainDuplicates()) {
      throw new IllegalArgumentException("Right source must not contain duplicates");
    }
    this.left = left;
    this.right = right;
  }

  @Override
  public @NotNull List<QueryNode<?>> sources() {
    return List.of(left, right);
  }

  @Override
  public @NotNull SortOrder<T> sortOrder() {
    return left.sortOrder();
  }

  @Override
  public boolean mayContainDuplicates() {
    return false;
  }

  @Override
  public @NotNull Stream<QueryData<T>> stream() {
    BiFunction<QueryData<T>, QueryData<T>, QueryData<T>> merger = QueryData.merger();
    Map<Integer, QueryData<T>> hashMap = new HashMap<>();
    right.stream().forEach(qd -> hashMap.put(qd.id(), qd));
    return left.stream()
        .filter(qd -> hashMap.containsKey(qd.id()))
        .map(qd -> merger.apply(qd, hashMap.get(qd.id())));
  }

  @Override
  public String toString() {
    return "HashJoin";
  }
}

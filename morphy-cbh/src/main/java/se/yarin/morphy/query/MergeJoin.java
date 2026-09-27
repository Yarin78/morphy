package se.yarin.morphy.query;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.ToIntFunction;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.jetbrains.annotations.NotNull;

public class MergeJoin<L, R> extends QueryNode<L> {
  private final @NotNull QueryNode<L> left;
  private final @NotNull QueryNode<R> right;
  private final @NotNull JoinType joinType;
  private final @NotNull ToIntFunction<QueryData<L>> leftKey;
  private final @NotNull ToIntFunction<QueryData<R>> rightKey;
  private final boolean sameType;
  private final @NotNull JoinCardinality cardinality;

  private MergeJoin(
      @NotNull QueryNode<L> left,
      @NotNull QueryNode<R> right,
      @NotNull JoinType joinType,
      @NotNull ToIntFunction<QueryData<L>> leftKey,
      @NotNull ToIntFunction<QueryData<R>> rightKey,
      boolean sameType,
      @NotNull JoinCardinality cardinality) {
    this.left = left;
    this.right = right;
    this.joinType = joinType;
    this.leftKey = leftKey;
    this.rightKey = rightKey;
    this.sameType = sameType;
    this.cardinality = cardinality;
  }

  public static <T> MergeJoin<T, T> inner(
      @NotNull QueryNode<T> left,
      @NotNull QueryNode<T> right,
      @NotNull JoinCardinality cardinality) {
    if (!left.sortOrder().isSameOrStronger(SortOrder.byId())) {
      throw new IllegalArgumentException("Left source must be sorted by id");
    }
    if (!right.sortOrder().isSameOrStronger(SortOrder.byId())) {
      throw new IllegalArgumentException("Right source must be sorted by id");
    }
    return new MergeJoin<>(
        left, right, JoinType.INNER, QueryData::id, QueryData::id, true, cardinality);
  }

  public static <L, R> MergeJoin<L, R> inner(
      @NotNull QueryNode<L> left,
      @NotNull QueryNode<R> right,
      @NotNull ToIntFunction<QueryData<L>> leftKey,
      @NotNull ToIntFunction<QueryData<R>> rightKey,
      @NotNull JoinCardinality cardinality) {
    return new MergeJoin<>(left, right, JoinType.INNER, leftKey, rightKey, false, cardinality);
  }

  public static <L, R> MergeJoin<L, R> semi(
      @NotNull QueryNode<L> left,
      @NotNull QueryNode<R> right,
      @NotNull ToIntFunction<QueryData<L>> leftKey,
      @NotNull ToIntFunction<QueryData<R>> rightKey) {
    return new MergeJoin<>(
        left, right, JoinType.SEMI, leftKey, rightKey, false, JoinCardinality.ONE_TO_ONE);
  }

  public static <T> MergeJoin<T, T> semi(
      @NotNull QueryNode<T> left, @NotNull QueryNode<T> right) {
    if (!left.sortOrder().isSameOrStronger(SortOrder.byId())) {
      throw new IllegalArgumentException("Left source must be sorted by id");
    }
    if (!right.sortOrder().isSameOrStronger(SortOrder.byId())) {
      throw new IllegalArgumentException("Right source must be sorted by id");
    }
    return new MergeJoin<>(
        left, right, JoinType.SEMI, QueryData::id, QueryData::id, true, JoinCardinality.ONE_TO_ONE);
  }

  public static <L, R> MergeJoin<L, R> anti(
      @NotNull QueryNode<L> left,
      @NotNull QueryNode<R> right,
      @NotNull ToIntFunction<QueryData<L>> leftKey,
      @NotNull ToIntFunction<QueryData<R>> rightKey) {
    return new MergeJoin<>(
        left, right, JoinType.ANTI, leftKey, rightKey, false, JoinCardinality.ONE_TO_ONE);
  }

  public static <T> MergeJoin<T, T> anti(
      @NotNull QueryNode<T> left, @NotNull QueryNode<T> right) {
    if (!left.sortOrder().isSameOrStronger(SortOrder.byId())) {
      throw new IllegalArgumentException("Left source must be sorted by id");
    }
    if (!right.sortOrder().isSameOrStronger(SortOrder.byId())) {
      throw new IllegalArgumentException("Right source must be sorted by id");
    }
    return new MergeJoin<>(
        left, right, JoinType.ANTI, QueryData::id, QueryData::id, true, JoinCardinality.ONE_TO_ONE);
  }

  public @NotNull JoinType joinType() {
    return joinType;
  }

  public @NotNull JoinCardinality cardinality() {
    return cardinality;
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
    return switch (joinType) {
      case INNER ->
          cardinality == JoinCardinality.ONE_TO_MANY
              ? left.mayContainDuplicates() || right.mayContainDuplicates()
              : left.mayContainDuplicates();
      case SEMI, ANTI -> left.mayContainDuplicates();
    };
  }

  @Override
  public @NotNull Stream<QueryData<L>> stream() {
    Iterator<QueryData<L>> leftIter = left.stream().iterator();
    Iterator<QueryData<R>> rightIter = right.stream().iterator();

    Iterator<QueryData<L>> mergeIter =
        new Iterator<>() {
          private QueryData<L> nextLeft = leftIter.hasNext() ? leftIter.next() : null;
          private QueryData<R> nextRight = rightIter.hasNext() ? rightIter.next() : null;
          private QueryData<L> pending = null;

          // Buffer of right rows matching the current key, used to handle duplicates.
          // When multiple left rows share the same key, the buffer is iterated once per left row.
          private List<QueryData<R>> rightBuffer = null;
          private int rightBufferIdx = 0;
          private int currentKey;

          private QueryData<L> advanceLeft() {
            return leftIter.hasNext() ? leftIter.next() : null;
          }

          private QueryData<R> advanceRight() {
            return rightIter.hasNext() ? rightIter.next() : null;
          }

          private void findNext() {
            if (pending != null) return;

            // Continue with buffered right matches from a previous key group
            if (rightBuffer != null) {
              if (joinType == JoinType.INNER) {
                // Continue cartesian product: left rows × buffered right rows
                while (nextLeft != null && leftKey.applyAsInt(nextLeft) == currentKey) {
                  if (rightBufferIdx < rightBuffer.size()) {
                    pending =
                        QueryData.combine(nextLeft, rightBuffer.get(rightBufferIdx), sameType);
                    rightBufferIdx++;
                    if (rightBufferIdx >= rightBuffer.size()) {
                      nextLeft = advanceLeft();
                      rightBufferIdx = 0;
                    }
                    return;
                  }
                  break;
                }
              } else if (joinType == JoinType.SEMI) {
                // Continue emitting left rows that share the matched key
                if (nextLeft != null && leftKey.applyAsInt(nextLeft) == currentKey) {
                  pending = QueryData.combine(nextLeft, rightBuffer.get(0), sameType);
                  nextLeft = advanceLeft();
                  return;
                }
              }
              // ANTI never enters here; key group done
              rightBuffer = null;
            }

            // Scan for next match/non-match
            while (nextLeft != null) {
              if (nextRight == null) {
                if (joinType == JoinType.ANTI) {
                  pending = nextLeft;
                  nextLeft = advanceLeft();
                  return;
                }
                return;
              }

              int lk = leftKey.applyAsInt(nextLeft);
              int rk = rightKey.applyAsInt(nextRight);

              if (lk < rk) {
                if (joinType == JoinType.ANTI) {
                  pending = nextLeft;
                  nextLeft = advanceLeft();
                  return;
                }
                nextLeft = advanceLeft();
              } else if (lk > rk) {
                nextRight = advanceRight();
              } else {
                // Match found — buffer all right rows with this key
                currentKey = lk;
                rightBuffer = new ArrayList<>();
                while (nextRight != null && rightKey.applyAsInt(nextRight) == currentKey) {
                  rightBuffer.add(nextRight);
                  nextRight = advanceRight();
                }

                switch (joinType) {
                  case INNER -> {
                    rightBufferIdx = 0;
                    pending = QueryData.combine(nextLeft, rightBuffer.get(0), sameType);
                    rightBufferIdx = 1;
                    if (rightBufferIdx >= rightBuffer.size()) {
                      nextLeft = advanceLeft();
                      rightBufferIdx = 0;
                    }
                    return;
                  }
                  case SEMI -> {
                    pending = QueryData.combine(nextLeft, rightBuffer.get(0), sameType);
                    nextLeft = advanceLeft();
                    return;
                  }
                  case ANTI -> {
                    // Skip all left rows with this key
                    while (nextLeft != null && leftKey.applyAsInt(nextLeft) == currentKey) {
                      nextLeft = advanceLeft();
                    }
                    rightBuffer = null;
                  }
                }
              }
            }
          }

          @Override
          public boolean hasNext() {
            findNext();
            return pending != null;
          }

          @Override
          public QueryData<L> next() {
            if (!hasNext()) throw new NoSuchElementException();
            var result = pending;
            pending = null;
            return result;
          }
        };

    Iterable<QueryData<L>> iterable = () -> mergeIter;
    return StreamSupport.stream(iterable.spliterator(), false);
  }

  @Override
  public String toString() {
    return "MergeJoin[" + joinType + "]";
  }
}

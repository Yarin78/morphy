package se.yarin.morphy.query;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.jetbrains.annotations.NotNull;

public class MergeJoin<T> extends QueryNode<T> {
  private final @NotNull QueryNode<T> left;
  private final @NotNull QueryNode<T> right;

  public MergeJoin(@NotNull QueryNode<T> left, @NotNull QueryNode<T> right) {
    if (!left.sortOrder().isSameOrStronger(SortOrder.byId())) {
      throw new IllegalArgumentException("Left source must be sorted by id");
    }
    if (!right.sortOrder().isSameOrStronger(SortOrder.byId())) {
      throw new IllegalArgumentException("Right source must be sorted by id");
    }
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
    return SortOrder.byId();
  }

  @Override
  public boolean mayContainDuplicates() {
    return false;
  }

  @Override
  public @NotNull Stream<QueryData<T>> stream() {
    BiFunction<QueryData<T>, QueryData<T>, QueryData<T>> merger = QueryData.merger();
    Iterator<QueryData<T>> leftIterator = left.stream().iterator();
    Iterator<QueryData<T>> rightIterator = right.stream().iterator();

    Iterator<QueryData<T>> mergeIterator =
        new Iterator<>() {
          private QueryData<T> nextLeft = null;
          private QueryData<T> nextRight = null;
          private boolean initialized = false;

          private void init() {
            nextLeft = leftIterator.hasNext() ? leftIterator.next() : null;
            nextRight = rightIterator.hasNext() ? rightIterator.next() : null;
            advance();
            initialized = true;
          }

          private void advance() {
            while (nextLeft != null && nextRight != null && nextLeft.id() != nextRight.id()) {
              if (nextLeft.id() < nextRight.id()) {
                nextLeft = leftIterator.hasNext() ? leftIterator.next() : null;
              } else {
                nextRight = rightIterator.hasNext() ? rightIterator.next() : null;
              }
            }
          }

          @Override
          public boolean hasNext() {
            if (!initialized) init();
            return nextLeft != null && nextRight != null;
          }

          @Override
          public QueryData<T> next() {
            if (!hasNext()) throw new NoSuchElementException();
            QueryData<T> result = merger.apply(nextLeft, nextRight);
            nextLeft = leftIterator.hasNext() ? leftIterator.next() : null;
            advance();
            return result;
          }
        };

    Iterable<QueryData<T>> iterable = () -> mergeIterator;
    return StreamSupport.stream(iterable.spliterator(), false);
  }

  @Override
  public String toString() {
    return "MergeJoin";
  }
}

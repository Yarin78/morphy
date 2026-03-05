package se.yarin.morphy.query;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.jetbrains.annotations.NotNull;

public class Distinct<T> extends QueryNode<T> {
  private final @NotNull QueryNode<T> source;

  public Distinct(@NotNull QueryNode<T> source) {
    this.source = source;
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
    return false;
  }

  @Override
  public @NotNull Stream<QueryData<T>> stream() {
    if (isSortedById()) {
      return sequentialDedup();
    }
    return hashDedup();
  }

  private boolean isSortedById() {
    SortOrder<T> so = source.sortOrder();
    if (so.isNone()) return false;
    SortField<T> first = so.sortFields().getFirst();
    return first.name().equals("id")
        && so.sortDirections().getFirst() == SortOrder.Direction.ASCENDING;
  }

  private Stream<QueryData<T>> sequentialDedup() {
    Iterator<QueryData<T>> sourceIterator = source.stream().iterator();

    Iterator<QueryData<T>> dedupIterator =
        new Iterator<>() {
          private QueryData<T> next = null;
          private int lastId = -1;

          @Override
          public boolean hasNext() {
            while (next == null && sourceIterator.hasNext()) {
              QueryData<T> candidate = sourceIterator.next();
              if (candidate.id() != lastId) {
                next = candidate;
                lastId = candidate.id();
              }
            }
            return next != null;
          }

          @Override
          public QueryData<T> next() {
            if (!hasNext()) throw new NoSuchElementException();
            QueryData<T> result = next;
            next = null;
            return result;
          }
        };

    Iterable<QueryData<T>> iterable = () -> dedupIterator;
    return StreamSupport.stream(iterable.spliterator(), false);
  }

  private Stream<QueryData<T>> hashDedup() {
    Set<Integer> seen = new HashSet<>();
    return source.stream().filter(qd -> seen.add(qd.id()));
  }

  @Override
  public String toString() {
    return "Distinct[" + (isSortedById() ? "sequential" : "hash") + "]";
  }
}

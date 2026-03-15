package se.yarin.morphy.query;

import java.util.List;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.entities.Entity;
import se.yarin.morphy.entities.EntityIndexReadTransaction;
import se.yarin.morphy.entities.filters.EntityFilter;

public class EntityIndexScan<T extends Entity & Comparable<T>> extends QueryNode<T> {
  private final @NotNull EntityIndexReadTransaction<T> txn;
  private final @NotNull SortOrder<T> entitySortOrder;
  private final @Nullable EntityFilter<T> filter;
  private final boolean reverse;

  public EntityIndexScan(
      @NotNull EntityIndexReadTransaction<T> txn,
      @NotNull SortOrder<T> entitySortOrder,
      @Nullable EntityFilter<T> filter,
      boolean reverse) {
    this.txn = txn;
    this.entitySortOrder = entitySortOrder;
    this.filter = filter;
    this.reverse = reverse;
  }

  @Override
  public @NotNull List<QueryNode<?>> sources() {
    return List.of();
  }

  @Override
  public @NotNull SortOrder<T> sortOrder() {
    return entitySortOrder;
  }

  @Override
  public boolean mayContainDuplicates() {
    return false;
  }

  @Override
  public @NotNull Stream<QueryData<T>> stream() {
    return streamRange(null, null);
  }

  public @NotNull Stream<QueryData<T>> streamRange(@Nullable T rangeStart, @Nullable T rangeEnd) {
    Stream<T> entityStream;
    if (reverse) {
      entityStream = txn.streamOrderedDescending(rangeStart, rangeEnd, filter);
    } else {
      entityStream = txn.streamOrderedAscending(rangeStart, rangeEnd, filter);
    }
    return entityStream.map(entity -> new QueryData<>(entity.id(), entity));
  }

  @Override
  public String toString() {
    return "EntityIndexScan["
        + (reverse ? "desc" : "asc")
        + (filter != null ? ", filtered" : "")
        + "]";
  }
}

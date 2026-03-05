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
  private final @Nullable T rangeStart;
  private final @Nullable T rangeEnd;
  private final @Nullable EntityFilter<T> entityFilter;
  private final @Nullable DataFilter<T> postFilter;
  private final boolean reverse;

  public EntityIndexScan(
      @NotNull EntityIndexReadTransaction<T> txn,
      @NotNull SortOrder<T> entitySortOrder,
      @Nullable T rangeStart,
      @Nullable T rangeEnd,
      @Nullable EntityFilter<T> entityFilter,
      @Nullable DataFilter<T> postFilter,
      boolean reverse) {
    this.txn = txn;
    this.entitySortOrder = entitySortOrder;
    this.rangeStart = rangeStart;
    this.rangeEnd = rangeEnd;
    this.entityFilter = entityFilter;
    this.postFilter = postFilter;
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
    Stream<T> entityStream;
    if (reverse) {
      entityStream = txn.streamOrderedDescending(rangeStart, rangeEnd, entityFilter);
    } else {
      entityStream = txn.streamOrderedAscending(rangeStart, rangeEnd, entityFilter);
    }
    Stream<QueryData<T>> result = entityStream.map(entity -> new QueryData<>(entity.id(), entity));
    if (postFilter != null) {
      result =
          result.filter(
              qd -> {
                assert qd.data() != null;
                return postFilter.matches(qd.data());
              });
    }
    return result;
  }

  @Override
  public String toString() {
    return "EntityIndexScan["
        + (reverse ? "desc" : "asc")
        + (rangeStart != null || rangeEnd != null ? ", range" : "")
        + (entityFilter != null ? ", entityFilter" : "")
        + (postFilter != null ? ", postFilter" : "")
        + "]";
  }
}

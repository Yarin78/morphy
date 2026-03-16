package se.yarin.morphy.query;

import java.util.List;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.entities.Entity;
import se.yarin.morphy.entities.EntityIndexReadTransaction;
import se.yarin.morphy.entities.filters.EntityFilter;

public class EntityTableScan<T extends Entity & Comparable<T>> extends QueryNode<T> {
  private final @NotNull EntityIndexReadTransaction<T> transaction;
  private final @Nullable Integer startId; // inclusive
  private final @Nullable Integer endId; // exclusive
  private final @Nullable EntityFilter<T> filter;

  public EntityTableScan(@NotNull EntityIndexReadTransaction<T> transaction) {
    this(transaction, null, null, null);
  }

  public EntityTableScan(
      @NotNull EntityIndexReadTransaction<T> transaction,
      @Nullable EntityFilter<T> filter) {
    this(transaction, null, null, filter);
  }

  public EntityTableScan(
      @NotNull EntityIndexReadTransaction<T> transaction,
      @Nullable Integer startId,
      @Nullable Integer endId,
      @Nullable EntityFilter<T> filter) {
    this.transaction = transaction;
    this.startId = startId;
    this.endId = endId;
    this.filter = filter;
  }

  @Override
  public @NotNull List<QueryNode<?>> sources() {
    return List.of();
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
    return transaction.stream(startId, endId, filter)
        .map(entity -> new QueryData<>(entity.id(), entity));
  }

  @Override
  public String toString() {
    int start = startId != null ? startId : 0;
    int end = endId != null ? endId : transaction.index().capacity();
    return "EntityTableScan[" + start + ".." + end + (filter != null ? ", filtered" : "") + "]";
  }
}

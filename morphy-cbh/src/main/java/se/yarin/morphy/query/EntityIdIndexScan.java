package se.yarin.morphy.query;

import java.util.List;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.entities.Entity;
import se.yarin.morphy.entities.EntityIndexReadTransaction;
import se.yarin.morphy.entities.filters.EntityFilter;

public class EntityIdIndexScan<T extends Entity & Comparable<T>> extends QueryNode<T>
    implements IndexScanNode<T, Integer> {
  private final @NotNull EntityIndexReadTransaction<T> txn;
  private final @Nullable EntityFilter<T> entityFilter;
  private final @Nullable DataFilter<T> postFilter;

  public EntityIdIndexScan(
      @NotNull EntityIndexReadTransaction<T> txn,
      @Nullable EntityFilter<T> entityFilter,
      @Nullable DataFilter<T> postFilter) {
    this.txn = txn;
    this.entityFilter = entityFilter;
    this.postFilter = postFilter;
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
    return streamRange(null, null);
  }

  @Override
  public @Nullable QueryData<T> getByKey(@NotNull Integer id) {
    T entity = txn.get(id);
    if (entityFilter != null && !entityFilter.matches(entity)) {
      return null;
    }
    QueryData<T> qd = new QueryData<>(entity.id(), entity);
    if (postFilter != null && !postFilter.matches(entity)) {
      return null;
    }
    return qd;
  }

  @Override
  public @NotNull Stream<QueryData<T>> streamRange(
      @Nullable Integer startId, @Nullable Integer endId) {
    Stream<QueryData<T>> result =
        txn.stream(startId, endId, entityFilter)
            .map(entity -> new QueryData<>(entity.id(), entity));
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
    return "EntityIdIndexScan["
        + (entityFilter != null ? "entityFilter" : "")
        + (postFilter != null ? (entityFilter != null ? ", " : "") + "postFilter" : "")
        + "]";
  }
}

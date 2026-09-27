package se.yarin.morphy.query;

import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.games.ExtendedGameHeader;
import se.yarin.morphy.games.ExtendedGameHeaderStorage;
import se.yarin.morphy.storage.ItemStorageFilter;

public class ExtendedGameHeaderIdIndexScan extends QueryNode<ExtendedGameHeader>
    implements IndexScanNode<ExtendedGameHeader, Integer> {
  private final @NotNull ExtendedGameHeaderStorage storage;
  private final @Nullable ItemStorageFilter<ExtendedGameHeader> filter;

  public ExtendedGameHeaderIdIndexScan(
      @NotNull DatabaseReadTransaction txn,
      @Nullable ItemStorageFilter<ExtendedGameHeader> filter) {
    this.storage = txn.database().extendedGameHeaderStorage();
    this.filter = filter;
  }

  public ExtendedGameHeaderIdIndexScan(@NotNull DatabaseReadTransaction txn) {
    this(txn, null);
  }

  @Override
  public @NotNull List<QueryNode<?>> sources() {
    return List.of();
  }

  @Override
  public @NotNull SortOrder<ExtendedGameHeader> sortOrder() {
    return SortOrder.byId();
  }

  @Override
  public boolean mayContainDuplicates() {
    return false;
  }

  @Override
  public @NotNull Stream<QueryData<ExtendedGameHeader>> stream() {
    return streamRange(1, storage.count() + 1);
  }

  @Override
  public @Nullable QueryData<ExtendedGameHeader> getByKey(@NotNull Integer id) {
    if (id < 1 || id > storage.count()) {
      return null;
    }
    ExtendedGameHeader egh = storage.get(id);
    if (filter != null && !filter.matches(egh)) {
      return null;
    }
    return new QueryData<>(id, egh);
  }

  @Override
  public @NotNull Stream<QueryData<ExtendedGameHeader>> streamRange(
      @Nullable Integer startId, @Nullable Integer endId) {
    int start = startId != null ? startId : 1;
    int end = endId != null ? endId : storage.count() + 1;
    Stream<QueryData<ExtendedGameHeader>> result =
        IntStream.range(start, end)
            .mapToObj(id -> new QueryData<>(id, storage.get(id)));
    if (filter != null) {
      result = result.filter(qd -> filter.matches(qd.data()));
    }
    return result;
  }

  @Override
  public String toString() {
    return "ExtendedGameHeaderIdIndexScan[" + (filter != null ? "filtered" : "") + "]";
  }
}

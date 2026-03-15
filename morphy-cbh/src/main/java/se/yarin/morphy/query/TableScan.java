package se.yarin.morphy.query;

import java.util.List;
import java.util.Objects;
import java.util.function.IntFunction;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.entities.Entity;
import se.yarin.morphy.entities.EntityIndexReadTransaction;
import se.yarin.morphy.games.GameHeader;
import se.yarin.morphy.games.GameHeaderIndex;
import se.yarin.morphy.storage.ItemStorageFilter;

public class TableScan<T> extends QueryNode<T> {
  private final @NotNull IntFunction<@Nullable T> fetcher;
  private final int startId; // inclusive
  private final int endId; // exclusive
  private final @Nullable ItemStorageFilter<T> filter;

  public TableScan(
      @NotNull IntFunction<@Nullable T> fetcher,
      int startId,
      int endId,
      @Nullable ItemStorageFilter<T> filter) {
    if (startId < 0 || endId < startId) {
      throw new IllegalArgumentException(
          "Invalid range: startId=" + startId + ", endId=" + endId);
    }
    this.fetcher = fetcher;
    this.startId = startId;
    this.endId = endId;
    this.filter = filter;
  }

  public TableScan(@NotNull IntFunction<@Nullable T> fetcher, int startId, int endId) {
    this(fetcher, startId, endId, null);
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
    // TODO: Could consider more efficient implementations here,
    // instead of getting items one by one.
    // Maybe change the IntFunction to something taking a range.
    return IntStream.range(startId, endId)
        .mapToObj(
            id -> {
              T data = fetcher.apply(id);
              if (data == null) return null;
              return new QueryData<>(id, data);
            })
        .filter(Objects::nonNull)
        .filter(
            qd -> {
              if (filter == null) return true;
              assert qd.data() != null;
              return filter.matches(qd.data());
            });
  }

  public static TableScan<GameHeader> gameHeaders(@NotNull DatabaseReadTransaction txn) {
    return gameHeaders(txn, null);
  }

  public static TableScan<GameHeader> gameHeaders(
      @NotNull DatabaseReadTransaction txn, @Nullable ItemStorageFilter<GameHeader> filter) {
    GameHeaderIndex index = txn.database().gameHeaderIndex();
    return new TableScan<>(index::getGameHeader, 1, index.count() + 1, filter);
  }

  public static TableScan<GameHeader> gameHeaders(
      @NotNull DatabaseReadTransaction txn,
      int startId,
      int endId,
      @Nullable ItemStorageFilter<GameHeader> filter) {
    return new TableScan<>(txn.database().gameHeaderIndex()::getGameHeader, startId, endId, filter);
  }

  public static <T extends Entity & Comparable<T>> TableScan<T> entities(
      @NotNull EntityIndexReadTransaction<T> txn) {
    return entities(txn, null);
  }

  public static <T extends Entity & Comparable<T>> TableScan<T> entities(
      @NotNull EntityIndexReadTransaction<T> txn, @Nullable ItemStorageFilter<T> filter) {
    return new TableScan<>(txn::get, 0, txn.index().count(), filter);
  }

  @Override
  public String toString() {
    return "TableScan[" + startId + ".." + endId + (filter != null ? ", filtered" : "") + "]";
  }
}

package se.yarin.morphy.query;

import java.util.List;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.games.ExtendedGameHeader;
import se.yarin.morphy.games.ExtendedGameHeaderStorage;
import se.yarin.morphy.games.GameHeader;
import se.yarin.morphy.games.GameHeaderIndex;
import se.yarin.morphy.storage.ItemStorage;
import se.yarin.morphy.storage.ItemStorageFilter;

public class TableScan<T> extends QueryNode<T> {
  private final @NotNull ItemStorage<?, T> storage;
  private final int startId; // inclusive
  private final int endId; // exclusive
  private final @Nullable ItemStorageFilter<T> filter;

  public TableScan(
      @NotNull ItemStorage<?, T> storage,
      int startId,
      int endId,
      @Nullable ItemStorageFilter<T> filter) {
    if (startId < 0 || endId < startId) {
      throw new IllegalArgumentException(
          "Invalid range: startId=" + startId + ", endId=" + endId);
    }
    this.storage = storage;
    this.startId = startId;
    this.endId = endId;
    this.filter = filter;
  }

  public TableScan(@NotNull ItemStorage<?, T> storage, int startId, int endId) {
    this(storage, startId, endId, null);
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
    return storage.stream(startId, endId, filter)
        .map(item -> new QueryData<>(item.index(), item.item()));
  }

  public static TableScan<GameHeader> gameHeaders(@NotNull DatabaseReadTransaction txn) {
    return gameHeaders(txn, null);
  }

  public static TableScan<GameHeader> gameHeaders(
      @NotNull DatabaseReadTransaction txn, @Nullable ItemStorageFilter<GameHeader> filter) {
    GameHeaderIndex index = txn.database().gameHeaderIndex();
    return new TableScan<>(index.storage(), 1, index.count() + 1, filter);
  }

  public static TableScan<GameHeader> gameHeaders(
      @NotNull DatabaseReadTransaction txn,
      int startId,
      int endId,
      @Nullable ItemStorageFilter<GameHeader> filter) {
    return new TableScan<>(txn.database().gameHeaderIndex().storage(), startId, endId, filter);
  }

  public static TableScan<ExtendedGameHeader> extendedGameHeaders(
      @NotNull DatabaseReadTransaction txn) {
    return extendedGameHeaders(txn, null);
  }

  public static TableScan<ExtendedGameHeader> extendedGameHeaders(
      @NotNull DatabaseReadTransaction txn,
      @Nullable ItemStorageFilter<ExtendedGameHeader> filter) {
    ExtendedGameHeaderStorage storage = txn.database().extendedGameHeaderStorage();
    return extendedGameHeaders(txn, 1, storage.count() + 1, filter);
  }

  public static TableScan<ExtendedGameHeader> extendedGameHeaders(
      @NotNull DatabaseReadTransaction txn,
      int startId,
      int endId,
      @Nullable ItemStorageFilter<ExtendedGameHeader> filter) {
    return new TableScan<>(txn.database().extendedGameHeaderStorage().storage(), startId, endId, filter);
  }

  @Override
  public String toString() {
    return "TableScan[" + startId + ".." + endId + (filter != null ? ", filtered" : "") + "]";
  }
}

package se.yarin.morphy.query;

import java.util.List;
import java.util.Objects;
import java.util.function.IntFunction;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.Database;
import se.yarin.morphy.entities.Entity;
import se.yarin.morphy.entities.EntityIndex;
import se.yarin.morphy.games.GameHeader;
import se.yarin.morphy.games.GameHeaderIndex;

public class TableScan<T> extends QueryNode<T> {
  private final @NotNull IntFunction<@Nullable T> fetcher;
  private final int startId; // inclusive
  private final int endId; // exclusive
  private final @Nullable DataFilter<T> filter;

  public TableScan(
      @NotNull IntFunction<@Nullable T> fetcher,
      int startId,
      int endId,
      @Nullable DataFilter<T> filter) {
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

  public static TableScan<GameHeader> gameHeaders(@NotNull Database db) {
    return gameHeaders(db, null);
  }

  public static TableScan<GameHeader> gameHeaders(
      @NotNull Database db, @Nullable DataFilter<GameHeader> filter) {
    GameHeaderIndex index = db.gameHeaderIndex();
    return new TableScan<>(index::getGameHeader, 1, index.count() + 1, filter);
  }

  public static TableScan<GameHeader> gameHeaders(
      @NotNull Database db, int startId, int endId, @Nullable DataFilter<GameHeader> filter) {
    return new TableScan<>(db.gameHeaderIndex()::getGameHeader, startId, endId, filter);
  }

  public static <T extends Entity & Comparable<T>> TableScan<T> entities(
      @NotNull EntityIndex<T> index) {
    return entities(index, null);
  }

  public static <T extends Entity & Comparable<T>> TableScan<T> entities(
      @NotNull EntityIndex<T> index, @Nullable DataFilter<T> filter) {
    return new TableScan<>(index::get, 0, index.count(), filter);
  }

  @Override
  public String toString() {
    return "TableScan[" + startId + ".." + endId + (filter != null ? ", filtered" : "") + "]";
  }
}

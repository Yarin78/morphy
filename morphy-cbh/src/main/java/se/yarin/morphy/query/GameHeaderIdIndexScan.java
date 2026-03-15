package se.yarin.morphy.query;

import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.games.GameHeader;
import se.yarin.morphy.storage.ItemStorageFilter;
import se.yarin.morphy.games.GameHeaderIndex;

public class GameHeaderIdIndexScan extends QueryNode<GameHeader>
    implements IndexScanNode<GameHeader, Integer> {
  private final @NotNull GameHeaderIndex index;
  private final @Nullable ItemStorageFilter<GameHeader> filter;

  public GameHeaderIdIndexScan(
      @NotNull DatabaseReadTransaction txn, @Nullable ItemStorageFilter<GameHeader> filter) {
    this.index = txn.database().gameHeaderIndex();
    this.filter = filter;
  }

  public GameHeaderIdIndexScan(@NotNull DatabaseReadTransaction txn) {
    this(txn, null);
  }

  @Override
  public @NotNull List<QueryNode<?>> sources() {
    return List.of();
  }

  @Override
  public @NotNull SortOrder<GameHeader> sortOrder() {
    return SortOrder.byId();
  }

  @Override
  public boolean mayContainDuplicates() {
    return false;
  }

  @Override
  public @NotNull Stream<QueryData<GameHeader>> stream() {
    return streamRange(1, index.count() + 1);
  }

  @Override
  public @Nullable QueryData<GameHeader> getByKey(@NotNull Integer id) {
    if (id < 1 || id > index.count()) {
      return null;
    }
    GameHeader gh = index.getGameHeader(id);
    if (filter != null && !filter.matches(gh)) {
      return null;
    }
    return new QueryData<>(gh.id(), gh);
  }

  @Override
  public @NotNull Stream<QueryData<GameHeader>> streamRange(
      @Nullable Integer startId, @Nullable Integer endId) {
    int start = startId != null ? startId : 1;
    int end = endId != null ? endId : index.count() + 1;
    Stream<QueryData<GameHeader>> result =
        IntStream.range(start, end)
            .mapToObj(
                id -> {
                  GameHeader gh = index.getGameHeader(id);
                  return new QueryData<>(gh.id(), gh);
                });
    if (filter != null) {
      result = result.filter(qd -> filter.matches(qd.data()));
    }
    return result;
  }

  @Override
  public String toString() {
    return "GameHeaderIdIndexScan[" + (filter != null ? "filtered" : "") + "]";
  }
}

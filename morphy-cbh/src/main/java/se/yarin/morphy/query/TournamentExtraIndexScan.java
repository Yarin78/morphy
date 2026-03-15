package se.yarin.morphy.query;

import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.entities.TournamentExtra;
import se.yarin.morphy.entities.TournamentExtraStorage;
import se.yarin.morphy.entities.TournamentIndexReadTransaction;
import se.yarin.morphy.storage.ItemStorageFilter;

public class TournamentExtraIndexScan extends QueryNode<TournamentExtra>
    implements IndexScanNode<TournamentExtra, Integer> {
  private final @NotNull TournamentIndexReadTransaction tournamentTxn;
  private final @NotNull TournamentExtraStorage storage;
  private final @Nullable ItemStorageFilter<TournamentExtra> filter;

  public TournamentExtraIndexScan(
      @NotNull DatabaseReadTransaction txn,
      @Nullable ItemStorageFilter<TournamentExtra> filter) {
    this.tournamentTxn = txn.tournamentTransaction();
    this.storage = txn.database().tournamentExtraStorage();
    this.filter = filter;
  }

  public TournamentExtraIndexScan(@NotNull DatabaseReadTransaction txn) {
    this(txn, null);
  }

  @Override
  public @NotNull List<QueryNode<?>> sources() {
    return List.of();
  }

  @Override
  public @NotNull SortOrder<TournamentExtra> sortOrder() {
    return SortOrder.byId();
  }

  @Override
  public boolean mayContainDuplicates() {
    return false;
  }

  @Override
  public @NotNull Stream<QueryData<TournamentExtra>> stream() {
    return streamRange(0, storage.numEntries());
  }

  @Override
  public @Nullable QueryData<TournamentExtra> getByKey(@NotNull Integer id) {
    TournamentExtra te = tournamentTxn.getExtra(id);
    if (filter != null && !filter.matches(te)) {
      return null;
    }
    return new QueryData<>(id, te);
  }

  @Override
  public @NotNull Stream<QueryData<TournamentExtra>> streamRange(
      @Nullable Integer startId, @Nullable Integer endId) {
    int start = startId != null ? startId : 0;
    int end = endId != null ? endId : storage.numEntries();
    Stream<QueryData<TournamentExtra>> result =
        IntStream.range(start, end)
            .mapToObj(id -> new QueryData<>(id, tournamentTxn.getExtra(id)));
    if (filter != null) {
      result = result.filter(qd -> filter.matches(qd.data()));
    }
    return result;
  }

  @Override
  public String toString() {
    return "TournamentExtraIndexScan[" + (filter != null ? "filtered" : "") + "]";
  }
}

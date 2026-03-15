package se.yarin.morphy.query;

import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.boosters.GameEntityIndex;
import se.yarin.morphy.entities.EntityType;

public class GameEntityIndexScan extends QueryNode<Void> {
  private final @NotNull GameEntityIndex gameEntityIndex;
  private final @NotNull EntityType entityType;

  public GameEntityIndexScan(
      @NotNull DatabaseReadTransaction txn, @NotNull EntityType entityType) {
    this.gameEntityIndex = txn.database().gameEntityIndex(entityType);
    this.entityType = entityType;
  }

  @Override
  public @NotNull List<QueryNode<?>> sources() {
    return List.of();
  }

  @Override
  public @NotNull SortOrder<Void> sortOrder() {
    return SortOrder.none();
  }

  @Override
  public boolean mayContainDuplicates() {
    return true;
  }

  @Override
  public @NotNull Stream<QueryData<Void>> stream() {
    throw new UnsupportedOperationException(
        "GameEntityIndexScan requires a range; use streamRange() instead");
  }

  public @NotNull Stream<QueryData<Void>> streamRange(
      @Nullable Integer startEntityId, @Nullable Integer endEntityId) {
    if (startEntityId == null || endEntityId == null) {
      throw new IllegalArgumentException("startEntityId and endEntityId must not be null");
    }
    return IntStream.range(startEntityId, endEntityId)
        .boxed()
        .flatMap(
            entityId ->
                gameEntityIndex.stream(entityId, entityType, false).map(QueryData::new));
  }

  @Override
  public String toString() {
    return "GameEntityIndexScan[" + entityType.nameSingular() + "]";
  }
}

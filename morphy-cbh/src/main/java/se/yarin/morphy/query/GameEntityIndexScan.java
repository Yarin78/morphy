package se.yarin.morphy.query;

import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.boosters.GameEntityIndex;
import se.yarin.morphy.entities.EntityType;

public class GameEntityIndexScan extends QueryNode<Void> {
  private final @NotNull GameEntityIndex gameEntityIndex;
  private final @NotNull EntityType entityType;
  private final int startEntityId; // inclusive
  private final int endEntityId; // exclusive

  public GameEntityIndexScan(
      @NotNull GameEntityIndex gameEntityIndex,
      @NotNull EntityType entityType,
      int startEntityId,
      int endEntityId) {
    if (startEntityId < 0 || endEntityId < startEntityId) {
      throw new IllegalArgumentException(
          "Invalid range: startEntityId=" + startEntityId + ", endEntityId=" + endEntityId);
    }
    this.gameEntityIndex = gameEntityIndex;
    this.entityType = entityType;
    this.startEntityId = startEntityId;
    this.endEntityId = endEntityId;
  }

  private boolean isSingleEntity() {
    return endEntityId - startEntityId == 1;
  }

  @Override
  public @NotNull List<QueryNode<?>> sources() {
    return List.of();
  }

  @Override
  public @NotNull SortOrder<Void> sortOrder() {
    return isSingleEntity() ? SortOrder.byId() : SortOrder.none();
  }

  @Override
  public boolean mayContainDuplicates() {
    return !isSingleEntity();
  }

  @Override
  public @NotNull Stream<QueryData<Void>> stream() {
    return IntStream.range(startEntityId, endEntityId)
        .boxed()
        .flatMap(
            entityId ->
                gameEntityIndex.stream(entityId, entityType, false)
                    .map(QueryData::new));
  }

  @Override
  public String toString() {
    return "GameEntityIndexScan["
        + entityType.nameSingular()
        + ", "
        + startEntityId
        + ".."
        + endEntityId
        + "]";
  }
}

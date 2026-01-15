package se.yarin.morphy.queries.operations;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.Game;
import se.yarin.morphy.IdObject;
import se.yarin.morphy.boosters.GameEntityIndex;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.metrics.MetricsProvider;
import se.yarin.morphy.queries.QueryContext;
import se.yarin.morphy.queries.QuerySortOrder;

import java.util.List;
import java.util.stream.Stream;

public class GameIdsByEntities<T extends IdObject> extends QueryOperator<Game> {
  private final @NotNull QueryOperator<T> source;
  private final @NotNull GameEntityIndex gameEntityIndex;
  private final @NotNull EntityType entityType;
  private final boolean singleSource;

  public GameIdsByEntities(
      @NotNull QueryContext queryContext,
      @NotNull QueryOperator<T> source,
      @NotNull EntityType entityType) {
    super(queryContext, false);
    // Note: This will return all games referencing any of the entities in the source
    // If a join condition different than ANY is used, further filtering is needed
    singleSource = (source instanceof Manual<?>) && (((Manual<T>) source).singleItem());

    GameEntityIndex gameEntityIndex =
        queryContext.transaction().database().gameEntityIndex(entityType);
    if (gameEntityIndex == null) {
      throw new IllegalArgumentException(
          "No game entity index exists for " + entityType.namePlural());
    }

    this.source = source;
    this.gameEntityIndex = gameEntityIndex;
    this.entityType = entityType;
  }

  @Override
  public List<QueryOperator<?>> sources() {
    return List.of(source);
  }

  public @NotNull QuerySortOrder<Game> sortOrder() {
    return singleSource ? QuerySortOrder.byId() : QuerySortOrder.none();
  }

  public boolean mayContainDuplicates() {
    return !singleSource;
  }

  @Override
  public Stream<QueryData<Game>> operatorStream() {
    return this.source.stream()
        .flatMap(
            data -> gameEntityIndex.stream(data.id(), entityType, false).map(QueryData<Game>::new));
  }

  @Override
  public void estimateOperatorCost(@NotNull ImmutableOperatorCost.Builder operatorCost) {
    OperatorCost sourceCost = source.getOperatorCost();

    int entityCount = Math.max(1, context().entityIndex(entityType).count());
    int gameCount = Math.max(1, context().database().count());

    long expectedMatchingGames = sourceCost.estimateRows() * gameCount / entityCount;

    operatorCost
        .estimateRows(OperatorCost.capRowEstimate(expectedMatchingGames))
        .estimateDeserializations(expectedMatchingGames / 13 + sourceCost.estimateRows())
        .estimatePageReads(
            context()
                .queryPlanner()
                .estimateGameEntityIndexPageReads(entityType, sourceCost.estimateRows()));
  }

  @Override
  public String toString() {
    return "GameIdsBy" + entityType.nameSingularCapitalized() + "Ids()";
  }

  @Override
  protected List<MetricsProvider> metricProviders() {
    return List.of(gameEntityIndex);
  }
}

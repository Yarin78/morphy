package se.yarin.morphy.queries.operations;

import java.util.List;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.Game;
import se.yarin.morphy.entities.EntityIndexReadTransaction;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.entities.GameTag;
import se.yarin.morphy.metrics.MetricsProvider;
import se.yarin.morphy.queries.QueryContext;
import se.yarin.morphy.queries.QuerySortOrder;

public class GameTagLookup extends QueryOperator<Game> {
  private final @NotNull QueryOperator<Game> source;
  private final @NotNull EntityIndexReadTransaction<GameTag> gameTagTransaction;

  public GameTagLookup(
      @NotNull QueryContext queryContext, @NotNull QueryOperator<Game> source) {
    super(queryContext, source.hasFullData());
    this.source = source;
    this.gameTagTransaction =
        (EntityIndexReadTransaction<GameTag>)
            transaction().entityTransaction(EntityType.GAME_TAG);
  }

  @Override
  public List<QueryOperator<?>> sources() {
    return List.of(source);
  }

  @Override
  public @NotNull QuerySortOrder<Game> sortOrder() {
    return source.sortOrder();
  }

  @Override
  public boolean mayContainDuplicates() {
    return source.mayContainDuplicates();
  }

  @Override
  protected Stream<QueryData<Game>> operatorStream() {
    return this.source.stream()
        .map(
            data -> {
              Game game = data.data();
              int tagId = game.gameTagId();
              if (game.guidingText() || tagId == -1) {
                return data.withExtra(GameTag.of(""));
              }
              GameTag gameTag = gameTagTransaction.get(tagId);
              return data.withExtra(gameTag);
            });
  }

  @Override
  protected void estimateOperatorCost(@NotNull ImmutableOperatorCost.Builder operatorCost) {
    OperatorCost sourceCost = source.getOperatorCost();
    operatorCost
        .estimateRows(sourceCost.estimateRows())
        .estimateDeserializations(sourceCost.estimateRows())
        .estimatePageReads(
            context().queryPlanner().estimateGamePageReads(sourceCost.estimateRows()));
  }

  @Override
  public String toString() {
    return "GameTagLookup";
  }

  @Override
  protected List<MetricsProvider> metricProviders() {
    return this.gameTagTransaction.metricsProviders();
  }
}

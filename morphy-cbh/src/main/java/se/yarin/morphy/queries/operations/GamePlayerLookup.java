package se.yarin.morphy.queries.operations;

import java.util.List;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.Game;
import se.yarin.morphy.entities.EntityIndexReadTransaction;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.entities.Player;
import se.yarin.morphy.metrics.MetricsProvider;
import se.yarin.morphy.queries.QueryContext;
import se.yarin.morphy.queries.QuerySortOrder;

public class GamePlayerLookup extends QueryOperator<Game> {
  private final @NotNull QueryOperator<Game> source;
  private final @NotNull EntityIndexReadTransaction<Player> playerTransaction;
  private final boolean white;

  public GamePlayerLookup(
      @NotNull QueryContext queryContext,
      @NotNull QueryOperator<Game> source,
      boolean white) {
    super(queryContext, source.hasFullData());
    this.source = source;
    this.playerTransaction =
        (EntityIndexReadTransaction<Player>)
            transaction().entityTransaction(EntityType.PLAYER);
    this.white = white;
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
              if (game.guidingText()) {
                return data.withExtra(Player.ofFullName(""));
              }
              int playerId =
                  white
                      ? game.whitePlayerId()
                      : game.blackPlayerId();
              Player player = playerTransaction.get(playerId);
              return data.withExtra(player);
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
    return "GamePlayerLookup(" + (white ? "white" : "black") + ")";
  }

  @Override
  protected List<MetricsProvider> metricProviders() {
    return this.playerTransaction.metricsProviders();
  }
}

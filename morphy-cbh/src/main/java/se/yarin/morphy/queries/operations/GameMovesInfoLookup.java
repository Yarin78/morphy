package se.yarin.morphy.queries.operations;

import java.util.List;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.chess.GameModel;
import se.yarin.chess.GameMovesModel;
import se.yarin.morphy.Game;
import se.yarin.morphy.metrics.MetricsProvider;
import se.yarin.morphy.queries.QueryContext;
import se.yarin.morphy.queries.QuerySortOrder;

/**
 * Lookup operator that loads the game model and derives notation and variation ply count, storing
 * the results as {@link GameMovesInfo} extra data.
 */
public class GameMovesInfoLookup extends QueryOperator<Game> {
  private static final Logger log = LoggerFactory.getLogger(GameMovesInfoLookup.class);
  private static final GameMovesInfo EMPTY = new GameMovesInfo("", 0);
  private static final int NOTATION_MAX_PLY = 20;

  private final @NotNull QueryOperator<Game> source;

  public GameMovesInfoLookup(
      @NotNull QueryContext queryContext, @NotNull QueryOperator<Game> source) {
    super(queryContext, source.hasFullData());
    this.source = source;
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
                return data.withExtra(EMPTY);
              }
              try {
                GameModel model = game.getModel();
                GameMovesModel moves = model.moves();
                String notation = moves.getNotation(NOTATION_MAX_PLY);
                int varPly = moves.countPly(true) - moves.countPly(false);
                return data.withExtra(
                    new GameMovesInfo(notation != null ? notation : "", varPly));
              } catch (Exception e) {
                log.debug("Failed to load moves for game {}", game.id(), e);
                return data.withExtra(EMPTY);
              }
            });
  }

  @Override
  protected void estimateOperatorCost(@NotNull ImmutableOperatorCost.Builder operatorCost) {
    OperatorCost sourceCost = source.getOperatorCost();
    // Loading game models is expensive: moves blob deserialization + page reads
    operatorCost
        .estimateRows(sourceCost.estimateRows())
        .estimateDeserializations(sourceCost.estimateRows())
        .estimatePageReads(sourceCost.estimateRows() * 2);
  }

  @Override
  public String toString() {
    return "GameMovesInfoLookup()";
  }

  @Override
  protected List<MetricsProvider> metricProviders() {
    return List.of();
  }
}

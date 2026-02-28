package se.yarin.morphy.queries.operations;

import java.util.List;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.Game;
import se.yarin.morphy.entities.Annotator;
import se.yarin.morphy.entities.EntityIndexReadTransaction;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.metrics.MetricsProvider;
import se.yarin.morphy.queries.QueryContext;
import se.yarin.morphy.queries.QuerySortOrder;

public class GameAnnotatorLookup extends QueryOperator<Game> {
  private final @NotNull QueryOperator<Game> source;
  private final @NotNull EntityIndexReadTransaction<Annotator> annotatorTransaction;

  public GameAnnotatorLookup(
      @NotNull QueryContext queryContext, @NotNull QueryOperator<Game> source) {
    super(queryContext, source.hasFullData());
    this.source = source;
    this.annotatorTransaction =
        (EntityIndexReadTransaction<Annotator>)
            transaction().entityTransaction(EntityType.ANNOTATOR);
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
                return data.withExtra(Annotator.of(""));
              }
              Annotator annotator = annotatorTransaction.get(game.annotatorId());
              return data.withExtra(annotator);
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
    return "GameAnnotatorLookup";
  }

  @Override
  protected List<MetricsProvider> metricProviders() {
    return this.annotatorTransaction.metricsProviders();
  }
}

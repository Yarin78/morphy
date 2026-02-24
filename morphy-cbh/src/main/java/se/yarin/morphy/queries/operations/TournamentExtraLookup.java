package se.yarin.morphy.queries.operations;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.entities.Tournament;
import se.yarin.morphy.entities.TournamentIndexReadTransaction;
import se.yarin.morphy.metrics.MetricsProvider;
import se.yarin.morphy.queries.QueryContext;
import se.yarin.morphy.queries.QuerySortOrder;

import java.util.List;
import java.util.stream.Stream;

public class TournamentExtraLookup extends QueryOperator<Tournament> {
  private final @NotNull QueryOperator<Tournament> source;
  private final @NotNull TournamentIndexReadTransaction txn;

  public TournamentExtraLookup(
      @NotNull QueryContext queryContext, @NotNull QueryOperator<Tournament> source) {
    super(queryContext, source.hasFullData());
    this.source = source;
    this.txn =
        (TournamentIndexReadTransaction) transaction().entityTransaction(EntityType.TOURNAMENT);
  }

  @Override
  public List<QueryOperator<?>> sources() {
    return List.of(source);
  }

  @Override
  public @NotNull QuerySortOrder<Tournament> sortOrder() {
    return source.sortOrder();
  }

  @Override
  public boolean mayContainDuplicates() {
    return source.mayContainDuplicates();
  }

  @Override
  protected Stream<QueryData<Tournament>> operatorStream() {
    return this.source.stream().map(data -> data.withExtra(txn.getExtra(data.id())));
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
    return "TournamentExtraLookup()";
  }

  @Override
  protected List<MetricsProvider> metricProviders() {
    return this.txn.metricsProviders();
  }
}

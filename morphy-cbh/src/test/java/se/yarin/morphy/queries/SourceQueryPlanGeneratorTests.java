package se.yarin.morphy.queries;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import se.yarin.morphy.Database;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.Game;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.entities.Source;
import se.yarin.morphy.entities.filters.CombinedFilter;
import se.yarin.morphy.entities.filters.EntityFilter;
import se.yarin.morphy.entities.filters.ManualFilter;
import se.yarin.morphy.entities.filters.SourceTitleFilter;
import se.yarin.morphy.queries.operations.*;

public class SourceQueryPlanGeneratorTests {
  private Database db;
  private QueryPlanner spyPlanner;
  private QueryOperator<Game> mockOperator;

  @Before
  public void setupContext() {
    this.db = new Database();

    QueryPlanner planner = new QueryPlanner(db);
    this.spyPlanner = spy(planner);
    this.db.setQueryPlanner(this.spyPlanner);

    this.mockOperator = mock(QueryOperator.class);
    when(mockOperator.debugString(anyBoolean())).thenReturn("mock");
    when(mockOperator.getOperatorCost()).thenReturn(ImmutableOperatorCost.builder().build());
    when(mockOperator.hasFullData()).thenReturn(true);

    doReturn(List.of(mockOperator)).when(spyPlanner).getGameQueryPlans(any(), any(), anyBoolean());
  }

  @Test
  public void sourceById() {
    ManualFilter<Source> filter = new ManualFilter<>(new int[] {7}, EntityType.SOURCE);
    EntityQuery<Source> query = new EntityQuery<>(db, EntityType.SOURCE, List.of(filter));

    try (var txn = new DatabaseReadTransaction(db)) {
      QueryContext qc = new QueryContext(txn, false);
      List<QueryOperator<Source>> plans =
          db.queryPlanner().getEntityQueryPlans(qc, query, true);

      this.assertPlanExists(
          plans, new EntityTableScan<>(qc, EntityType.SOURCE, filter, 7, 8));
      this.assertPlanExists(
          plans,
          new EntityLookup<Source>(qc, EntityType.SOURCE, new Manual<>(qc, Set.of(7)), null));
    }
  }

  @Test
  public void sourceByTitle() {
    SourceTitleFilter titleFilter = new SourceTitleFilter("Chess", true, false);
    EntityQuery<Source> query =
        new EntityQuery<>(db, EntityType.SOURCE, List.of(titleFilter));

    QueryPlanner planner = new QueryPlanner(db);
    try (var txn = new DatabaseReadTransaction(db)) {
      QueryContext qc = new QueryContext(txn, false);
      List<QueryOperator<Source>> plans = planner.getEntityQueryPlans(qc, query, true);

      this.assertPlanExists(
          plans, new EntityTableScan<>(qc, EntityType.SOURCE, titleFilter));
      this.assertPlanExists(
          plans,
          new EntityIndexRangeScan<>(
              qc,
              EntityType.SOURCE,
              titleFilter,
              Source.of("Chess"),
              Source.of("Chesszzz"),
              false));
    }
  }

  @Test
  public void sourceByGameQuery() {
    EntityQuery<Source> query =
        new EntityQuery<>(
            db,
            EntityType.SOURCE,
            List.of(),
            new GameQuery(db, List.of()),
            null,
            QuerySortOrder.bySourceDefaultIndex(),
            0);

    try (var txn = new DatabaseReadTransaction(db)) {
      QueryContext qc = new QueryContext(txn, false);
      List<QueryOperator<Source>> plans =
          db.queryPlanner().getEntityQueryPlans(qc, query, true);

      this.assertPlanExists(
          plans,
          new Sort<>(
              qc,
              new EntityLookup<>(
                  qc,
                  EntityType.SOURCE,
                  new Distinct<>(
                      qc,
                      new Sort<>(
                          qc,
                          new EntityIdsByGames<Source>(
                              qc, EntityType.SOURCE, mockOperator, null),
                          QuerySortOrder.byId())),
                  null),
              QuerySortOrder.bySourceDefaultIndex()));
    }
  }

  @Test
  public void multipleSources() {
    SourceTitleFilter titleFilter = new SourceTitleFilter("Chess", true, false);
    EntityQuery<Source> query =
        new EntityQuery<>(
            db,
            EntityType.SOURCE,
            List.of(titleFilter),
            new GameQuery(db, List.of()),
            null,
            QuerySortOrder.bySourceDefaultIndex(true),
            0);

    doReturn(10000L).when(spyPlanner).entityRangeEstimate(any(), any(), any());

    try (var txn = new DatabaseReadTransaction(db)) {
      QueryContext qc = new QueryContext(txn, false);
      List<QueryOperator<Source>> plans =
          db.queryPlanner().getEntityQueryPlans(qc, query, true);

      QueryOperator<Source> sourcesByGamesSub =
          new Distinct<>(
              qc,
              new Sort<>(
                  qc,
                  new EntityIdsByGames<Source>(
                      qc, EntityType.SOURCE, mockOperator, null),
                  QuerySortOrder.byId()));
      this.assertPlanExists(
          plans,
          new Sort<>(
              qc,
              new EntityLookup<Source>(qc, EntityType.SOURCE, sourcesByGamesSub, titleFilter),
              QuerySortOrder.bySourceDefaultIndex(true)));

      this.assertPlanExists(
          plans,
          new Sort<>(
              qc,
              new MergeJoin<>(
                  qc,
                  new EntityTableScan<>(qc, EntityType.SOURCE, titleFilter),
                  sourcesByGamesSub),
              QuerySortOrder.bySourceDefaultIndex(true)));

      QueryOperator<Source> rangeScanOp =
          new EntityIndexRangeScan<>(
              qc,
              EntityType.SOURCE,
              titleFilter,
              Source.of("Chess"),
              Source.of("Chesszzz"),
              true);
      this.assertPlanExists(
          plans,
          new Sort<>(
              qc,
              new HashJoin<>(qc, sourcesByGamesSub, rangeScanOp),
              QuerySortOrder.bySourceDefaultIndex(true)));

      this.assertPlanExists(plans, new HashJoin<>(qc, rangeScanOp, sourcesByGamesSub));
    }
  }

  private void assertPlanExists(
      List<QueryOperator<Source>> plans, QueryOperator<Source> expectedPlan) {
    String expected = expectedPlan.debugString(false);
    for (QueryOperator<Source> plan : plans) {
      if (plan.debugString(false).equals(expected)) {
        return;
      }
    }
    throw new AssertionError(
        "Expected plan not found: "
            + expected
            + "\nActual plans:\n"
            + String.join("\n", plans.stream().map(p -> p.debugString(false)).toList()));
  }
}

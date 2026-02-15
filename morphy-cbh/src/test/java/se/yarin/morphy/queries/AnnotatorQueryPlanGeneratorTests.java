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
import se.yarin.morphy.entities.Annotator;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.entities.filters.AnnotatorNameFilter;
import se.yarin.morphy.entities.filters.CombinedFilter;
import se.yarin.morphy.entities.filters.EntityFilter;
import se.yarin.morphy.entities.filters.ManualFilter;
import se.yarin.morphy.queries.operations.*;

public class AnnotatorQueryPlanGeneratorTests {
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
  public void annotatorById() {
    ManualFilter<Annotator> filter = new ManualFilter<>(new int[] {7}, EntityType.ANNOTATOR);
    EntityQuery<Annotator> query =
        new EntityQuery<>(db, EntityType.ANNOTATOR, List.of(filter));

    try (var txn = new DatabaseReadTransaction(db)) {
      QueryContext qc = new QueryContext(txn, false);
      List<QueryOperator<Annotator>> plans =
          db.queryPlanner().getEntityQueryPlans(qc, query, true);

      this.assertPlanExists(
          plans, new EntityTableScan<>(qc, EntityType.ANNOTATOR, filter, 7, 8));
      this.assertPlanExists(
          plans,
          new EntityLookup<Annotator>(qc, EntityType.ANNOTATOR, new Manual<>(qc, Set.of(7)), null));
    }
  }

  @Test
  public void annotatorByName() {
    AnnotatorNameFilter nameFilter = new AnnotatorNameFilter("Kas", true, false);
    EntityQuery<Annotator> query =
        new EntityQuery<>(db, EntityType.ANNOTATOR, List.of(nameFilter));

    QueryPlanner planner = new QueryPlanner(db);
    try (var txn = new DatabaseReadTransaction(db)) {
      QueryContext qc = new QueryContext(txn, false);
      List<QueryOperator<Annotator>> plans = planner.getEntityQueryPlans(qc, query, true);

      this.assertPlanExists(
          plans, new EntityTableScan<>(qc, EntityType.ANNOTATOR, nameFilter));
      this.assertPlanExists(
          plans,
          new EntityIndexRangeScan<>(
              qc,
              EntityType.ANNOTATOR,
              nameFilter,
              Annotator.of("Kas"),
              Annotator.of("Kaszzz"),
              false));
    }
  }

  @Test
  public void annotatorByGameQuery() {
    EntityQuery<Annotator> query =
        new EntityQuery<>(
            db,
            EntityType.ANNOTATOR,
            List.of(),
            new GameQuery(db, List.of()),
            null,
            QuerySortOrder.byAnnotatorDefaultIndex(),
            0);

    try (var txn = new DatabaseReadTransaction(db)) {
      QueryContext qc = new QueryContext(txn, false);
      List<QueryOperator<Annotator>> plans =
          db.queryPlanner().getEntityQueryPlans(qc, query, true);

      this.assertPlanExists(
          plans,
          new Sort<>(
              qc,
              new EntityLookup<>(
                  qc,
                  EntityType.ANNOTATOR,
                  new Distinct<>(
                      qc,
                      new Sort<>(
                          qc,
                          new EntityIdsByGames<Annotator>(
                              qc, EntityType.ANNOTATOR, mockOperator, null),
                          QuerySortOrder.byId())),
                  null),
              QuerySortOrder.byAnnotatorDefaultIndex()));
    }
  }

  @Test
  public void multipleSources() {
    AnnotatorNameFilter nameFilter = new AnnotatorNameFilter("Kas", true, false);
    EntityQuery<Annotator> query =
        new EntityQuery<>(
            db,
            EntityType.ANNOTATOR,
            List.of(nameFilter),
            new GameQuery(db, List.of()),
            null,
            QuerySortOrder.byAnnotatorDefaultIndex(true),
            0);

    doReturn(10000L).when(spyPlanner).entityRangeEstimate(any(), any(), any());

    try (var txn = new DatabaseReadTransaction(db)) {
      QueryContext qc = new QueryContext(txn, false);
      List<QueryOperator<Annotator>> plans =
          db.queryPlanner().getEntityQueryPlans(qc, query, true);

      QueryOperator<Annotator> annotatorsByGamesSub =
          new Distinct<>(
              qc,
              new Sort<>(
                  qc,
                  new EntityIdsByGames<Annotator>(
                      qc, EntityType.ANNOTATOR, mockOperator, null),
                  QuerySortOrder.byId()));
      this.assertPlanExists(
          plans,
          new Sort<>(
              qc,
              new EntityLookup<Annotator>(qc, EntityType.ANNOTATOR, annotatorsByGamesSub, nameFilter),
              QuerySortOrder.byAnnotatorDefaultIndex(true)));

      this.assertPlanExists(
          plans,
          new Sort<>(
              qc,
              new MergeJoin<>(
                  qc,
                  new EntityTableScan<>(qc, EntityType.ANNOTATOR, nameFilter),
                  annotatorsByGamesSub),
              QuerySortOrder.byAnnotatorDefaultIndex(true)));

      QueryOperator<Annotator> rangeScanOp =
          new EntityIndexRangeScan<>(
              qc,
              EntityType.ANNOTATOR,
              nameFilter,
              Annotator.of("Kas"),
              Annotator.of("Kaszzz"),
              true);
      this.assertPlanExists(
          plans,
          new Sort<>(
              qc,
              new HashJoin<>(qc, annotatorsByGamesSub, rangeScanOp),
              QuerySortOrder.byAnnotatorDefaultIndex(true)));

      this.assertPlanExists(plans, new HashJoin<>(qc, rangeScanOp, annotatorsByGamesSub));
    }
  }

  private void assertPlanExists(
      List<QueryOperator<Annotator>> plans, QueryOperator<Annotator> expectedPlan) {
    String expected = expectedPlan.debugString(false);
    for (QueryOperator<Annotator> plan : plans) {
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

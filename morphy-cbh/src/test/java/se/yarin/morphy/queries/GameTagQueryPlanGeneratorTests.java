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
import se.yarin.morphy.entities.GameTag;
import se.yarin.morphy.entities.filters.CombinedFilter;
import se.yarin.morphy.entities.filters.EntityFilter;
import se.yarin.morphy.entities.filters.GameTagTitleFilter;
import se.yarin.morphy.entities.filters.ManualFilter;
import se.yarin.morphy.queries.operations.*;

public class GameTagQueryPlanGeneratorTests {
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
  public void gameTagById() {
    ManualFilter<GameTag> filter = new ManualFilter<>(new int[] {7}, EntityType.GAME_TAG);
    EntityQuery<GameTag> query = new EntityQuery<>(db, EntityType.GAME_TAG, List.of(filter));

    try (var txn = new DatabaseReadTransaction(db)) {
      QueryContext qc = new QueryContext(txn, false);
      List<QueryOperator<GameTag>> plans =
          db.queryPlanner().getEntityQueryPlans(qc, query, true);

      this.assertPlanExists(
          plans, new EntityTableScan<>(qc, EntityType.GAME_TAG, filter, 7, 8));
      this.assertPlanExists(
          plans,
          new EntityLookup<GameTag>(qc, EntityType.GAME_TAG, new Manual<>(qc, Set.of(7)), null));
    }
  }

  @Test
  public void gameTagByTitle() {
    GameTagTitleFilter titleFilter = new GameTagTitleFilter("Open", true, false);
    EntityQuery<GameTag> query =
        new EntityQuery<>(db, EntityType.GAME_TAG, List.of(titleFilter));

    QueryPlanner planner = new QueryPlanner(db);
    try (var txn = new DatabaseReadTransaction(db)) {
      QueryContext qc = new QueryContext(txn, false);
      List<QueryOperator<GameTag>> plans = planner.getEntityQueryPlans(qc, query, true);

      this.assertPlanExists(
          plans, new EntityTableScan<>(qc, EntityType.GAME_TAG, titleFilter));
      this.assertPlanExists(
          plans,
          new EntityIndexRangeScan<>(
              qc,
              EntityType.GAME_TAG,
              titleFilter,
              GameTag.of("Open"),
              GameTag.of("Openzzz"),
              false));
    }
  }

  @Test
  public void gameTagByGameQuery() {
    EntityQuery<GameTag> query =
        new EntityQuery<>(
            db,
            EntityType.GAME_TAG,
            List.of(),
            new GameQuery(db, List.of()),
            null,
            QuerySortOrder.byGameTagDefaultIndex(),
            0);

    try (var txn = new DatabaseReadTransaction(db)) {
      QueryContext qc = new QueryContext(txn, false);
      List<QueryOperator<GameTag>> plans =
          db.queryPlanner().getEntityQueryPlans(qc, query, true);

      this.assertPlanExists(
          plans,
          new Sort<>(
              qc,
              new EntityLookup<>(
                  qc,
                  EntityType.GAME_TAG,
                  new Distinct<>(
                      qc,
                      new Sort<>(
                          qc,
                          new EntityIdsByGames<GameTag>(
                              qc, EntityType.GAME_TAG, mockOperator, null),
                          QuerySortOrder.byId())),
                  null),
              QuerySortOrder.byGameTagDefaultIndex()));
    }
  }

  @Test
  public void multipleSources() {
    GameTagTitleFilter titleFilter = new GameTagTitleFilter("Open", true, false);
    EntityQuery<GameTag> query =
        new EntityQuery<>(
            db,
            EntityType.GAME_TAG,
            List.of(titleFilter),
            new GameQuery(db, List.of()),
            null,
            QuerySortOrder.byGameTagDefaultIndex(true),
            0);

    doReturn(10000L).when(spyPlanner).entityRangeEstimate(any(), any(), any());

    try (var txn = new DatabaseReadTransaction(db)) {
      QueryContext qc = new QueryContext(txn, false);
      List<QueryOperator<GameTag>> plans =
          db.queryPlanner().getEntityQueryPlans(qc, query, true);

      QueryOperator<GameTag> gameTagsByGamesSub =
          new Distinct<>(
              qc,
              new Sort<>(
                  qc,
                  new EntityIdsByGames<GameTag>(
                      qc, EntityType.GAME_TAG, mockOperator, null),
                  QuerySortOrder.byId()));
      this.assertPlanExists(
          plans,
          new Sort<>(
              qc,
              new EntityLookup<GameTag>(qc, EntityType.GAME_TAG, gameTagsByGamesSub, titleFilter),
              QuerySortOrder.byGameTagDefaultIndex(true)));

      this.assertPlanExists(
          plans,
          new Sort<>(
              qc,
              new MergeJoin<>(
                  qc,
                  new EntityTableScan<>(qc, EntityType.GAME_TAG, titleFilter),
                  gameTagsByGamesSub),
              QuerySortOrder.byGameTagDefaultIndex(true)));

      QueryOperator<GameTag> rangeScanOp =
          new EntityIndexRangeScan<>(
              qc,
              EntityType.GAME_TAG,
              titleFilter,
              GameTag.of("Open"),
              GameTag.of("Openzzz"),
              true);
      this.assertPlanExists(
          plans,
          new Sort<>(
              qc,
              new HashJoin<>(qc, gameTagsByGamesSub, rangeScanOp),
              QuerySortOrder.byGameTagDefaultIndex(true)));

      this.assertPlanExists(plans, new HashJoin<>(qc, rangeScanOp, gameTagsByGamesSub));
    }
  }

  private void assertPlanExists(
      List<QueryOperator<GameTag>> plans, QueryOperator<GameTag> expectedPlan) {
    String expected = expectedPlan.debugString(false);
    for (QueryOperator<GameTag> plan : plans) {
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

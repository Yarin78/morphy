package se.yarin.morphy.queries;

import org.junit.Before;
import org.junit.Test;
import se.yarin.morphy.Database;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.Game;
import se.yarin.morphy.ResourceLoader;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.entities.Player;
import se.yarin.morphy.entities.filters.CombinedFilter;
import se.yarin.morphy.entities.filters.EntityFilter;
import se.yarin.morphy.entities.filters.ManualFilter;
import se.yarin.morphy.entities.filters.PlayerNameFilter;
import se.yarin.morphy.queries.operations.*;

import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class PlayerQueryPlanGeneratorTests {
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
  public void playerById() {
    ManualFilter<Player> playerFilter = new ManualFilter<>(new int[] {7}, EntityType.PLAYER);
    EntityQuery<Player> playerQuery =
        new EntityQuery<>(db, EntityType.PLAYER, List.of(playerFilter));

    try (var txn = new DatabaseReadTransaction(db)) {
      QueryContext qc = new QueryContext(txn, false);
      List<QueryOperator<Player>> plans =
          db.queryPlanner().getEntityQueryPlans(qc, playerQuery, true);

      this.assertPlanExists(
          plans, new EntityTableScan<>(qc, EntityType.PLAYER, playerFilter, 7, 8));
      this.assertPlanExists(
          plans, new EntityLookup(qc, EntityType.PLAYER, new Manual<>(qc, Set.of(7)), null));
    }
  }

  @Test
  public void playerByGameQuery() {
    EntityQuery<Player> playerQuery =
        new EntityQuery<Player>(
            db,
            EntityType.PLAYER,
            List.of(),
            new GameQuery(db, List.of()),
            GameEntityJoinCondition.ANY,
            QuerySortOrder.byPlayerDefaultIndex(),
            0);

    try (var txn = new DatabaseReadTransaction(db)) {
      QueryContext qc = new QueryContext(txn, false);
      List<QueryOperator<Player>> plans =
          db.queryPlanner().getEntityQueryPlans(qc, playerQuery, true);

      this.assertPlanExists(
          plans,
          new Sort<>(
              qc,
              new EntityLookup<>(
                  qc,
                  EntityType.PLAYER,
                  new Distinct<>(
                      qc,
                      new Sort<>(
                          qc,
                          new EntityIdsByGames<Player>(
                              qc, EntityType.PLAYER, mockOperator, GameEntityJoinCondition.ANY),
                          QuerySortOrder.byId())),
                  null),
              QuerySortOrder.byPlayerDefaultIndex()));
    }
  }

  @Test
  public void playerByPrefixNameFilter() {
    PlayerNameFilter kaspFilter = new PlayerNameFilter("Kasp", "", true, false);
    EntityQuery<Player> pq = new EntityQuery<Player>(db, EntityType.PLAYER, List.of(kaspFilter));

    QueryPlanner planner = new QueryPlanner(db);
    try (var txn = new DatabaseReadTransaction(db)) {
      QueryContext qc = new QueryContext(txn, false);
      List<QueryOperator<Player>> plans = planner.getEntityQueryPlans(qc, pq, true);

      this.assertPlanExists(plans, new EntityTableScan<>(qc, EntityType.PLAYER, kaspFilter));
      this.assertPlanExists(
          plans,
          new EntityIndexRangeScan<>(
              qc,
              EntityType.PLAYER,
              kaspFilter,
              Player.of("Kasp", ""),
              Player.of("Kaspzzz", ""),
              false));
    }
  }

  @Test
  public void playerByIdSortedByName() {
    ManualFilter<Player> manualFilter =
        new ManualFilter<>(new int[] {8, 12, 15, 17}, EntityType.PLAYER);
    EntityQuery<Player> pq =
        new EntityQuery<>(
            db, EntityType.PLAYER, List.of(manualFilter), QuerySortOrder.byPlayerDefaultIndex(), 0);

    QueryPlanner planner = new QueryPlanner(db);
    try (var txn = new DatabaseReadTransaction(db)) {
      QueryContext qc = new QueryContext(txn, false);
      List<QueryOperator<Player>> plans = planner.getEntityQueryPlans(qc, pq, true);

      assertEquals(3, plans.size());
      this.assertPlanExists(
          plans,
          new Sort<>(
              qc,
              new EntityTableScan<>(qc, EntityType.PLAYER, manualFilter, 8, 18),
              QuerySortOrder.byPlayerDefaultIndex()));
      this.assertPlanExists(
          plans,
          new Sort<>(
              qc,
              new EntityLookup<Player>(
                  qc, EntityType.PLAYER, new Manual<>(qc, Set.of(8, 12, 15, 17)), null),
              QuerySortOrder.byPlayerDefaultIndex()));
      this.assertPlanExists(
          plans,
          new EntityIndexRangeScan<>(qc, EntityType.PLAYER, manualFilter, null, null, false));
    }
  }

  @Test
  public void playerByCaseInsensitiveNameExactMatch() {
    PlayerNameFilter kasparovFilter = new PlayerNameFilter("kasparov", "garry", false, true);
    EntityQuery<Player> pq = new EntityQuery<>(db, EntityType.PLAYER, List.of(kasparovFilter));

    QueryPlanner planner = new QueryPlanner(db);
    try (var txn = new DatabaseReadTransaction(db)) {
      QueryContext qc = new QueryContext(txn, false);
      List<QueryOperator<Player>> plans = planner.getEntityQueryPlans(qc, pq, true);

      this.assertPlanExists(plans, new EntityTableScan<>(qc, EntityType.PLAYER, kasparovFilter));
      this.assertPlanExists(
          plans,
          new EntityIndexRangeScan<>(qc, EntityType.PLAYER, kasparovFilter, null, null, false));
    }
  }

  @Test
  public void multiplePlayerNameFilters() {
    PlayerNameFilter f1 = new PlayerNameFilter("Car", "", true, false);
    PlayerNameFilter f2 = new PlayerNameFilter("Kar", "", true, false);
    EntityFilter<Player> combined = CombinedFilter.combine(List.of(f1, f2));

    EntityQuery<Player> pq = new EntityQuery<Player>(db, EntityType.PLAYER, List.of(f1, f2));

    QueryPlanner planner = new QueryPlanner(db);
    try (var txn = new DatabaseReadTransaction(db)) {
      QueryContext qc = new QueryContext(txn, false);
      List<QueryOperator<Player>> plans = planner.getEntityQueryPlans(qc, pq, true);

      this.assertPlanExists(plans, new EntityTableScan<>(qc, EntityType.PLAYER, combined));
      this.assertPlanExists(
          plans,
          new EntityIndexRangeScan(
              qc,
              EntityType.PLAYER,
              combined,
              Player.of("Kar", ""),
              Player.of("Carzzz", ""),
              false));
    }
  }

  @Test
  public void multipleSources() {
    PlayerNameFilter kaspFilter = new PlayerNameFilter("Kasp", "", true, false);
    EntityQuery<Player> playerQuery =
        new EntityQuery<Player>(
            db,
            EntityType.PLAYER,
            List.of(kaspFilter),
            new GameQuery(db, List.of()),
            GameEntityJoinCondition.ANY,
            QuerySortOrder.byPlayerDefaultIndex(true),
            0);

    // Ensure the PlayerIndexRangeScan is by default sorted after the PlayerIdsByGames operator
    doReturn(10000L).when(spyPlanner).entityRangeEstimate(any(), any(), any());

    try (var txn = new DatabaseReadTransaction(db)) {
      QueryContext qc = new QueryContext(txn, false);
      List<QueryOperator<Player>> plans =
          db.queryPlanner().getEntityQueryPlans(qc, playerQuery, true);

      QueryOperator<Player> playersByGamesSub =
          new Distinct<>(
              qc,
              new Sort<>(
                  qc,
                  new EntityIdsByGames<Player>(
                      qc, EntityType.PLAYER, mockOperator, GameEntityJoinCondition.ANY),
                  QuerySortOrder.byId()));
      this.assertPlanExists(
          plans,
          new Sort<>(
              qc,
              new EntityLookup<>(qc, EntityType.PLAYER, playersByGamesSub, kaspFilter),
              QuerySortOrder.byPlayerDefaultIndex(true)));

      this.assertPlanExists(
          plans,
          new Sort<>(
              qc,
              new MergeJoin<>(
                  qc, new EntityTableScan<>(qc, EntityType.PLAYER, kaspFilter), playersByGamesSub),
              QuerySortOrder.byPlayerDefaultIndex(true)));

      QueryOperator<Player> rangeScanOp =
          new EntityIndexRangeScan<>(
              qc,
              EntityType.PLAYER,
              kaspFilter,
              Player.of("Kasp", ""),
              Player.of("Kaspzzz", ""),
              true);
      this.assertPlanExists(
          plans,
          new Sort<>(
              qc,
              new HashJoin<>(qc, playersByGamesSub, rangeScanOp),
              QuerySortOrder.byPlayerDefaultIndex(true)));

      this.assertPlanExists(plans, new HashJoin<>(qc, rangeScanOp, playersByGamesSub));
    }
  }

  private void showPlans(List<QueryOperator<Player>> plans) {
    for (QueryOperator<Player> plan : plans) {
      System.out.println(plan.debugString(false));
      System.out.println("---");
    }
  }

  private void assertPlanExists(
      List<QueryOperator<Player>> plans, QueryOperator<Player> expectedPlan) {
    String expected = expectedPlan.debugString(false);
    for (QueryOperator<Player> plan : plans) {
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

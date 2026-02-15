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
import se.yarin.morphy.entities.Team;
import se.yarin.morphy.entities.filters.CombinedFilter;
import se.yarin.morphy.entities.filters.EntityFilter;
import se.yarin.morphy.entities.filters.ManualFilter;
import se.yarin.morphy.entities.filters.TeamTitleFilter;
import se.yarin.morphy.queries.operations.*;

public class TeamQueryPlanGeneratorTests {
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
  public void teamById() {
    ManualFilter<Team> filter = new ManualFilter<>(new int[] {7}, EntityType.TEAM);
    EntityQuery<Team> query = new EntityQuery<>(db, EntityType.TEAM, List.of(filter));

    try (var txn = new DatabaseReadTransaction(db)) {
      QueryContext qc = new QueryContext(txn, false);
      List<QueryOperator<Team>> plans =
          db.queryPlanner().getEntityQueryPlans(qc, query, true);

      this.assertPlanExists(
          plans, new EntityTableScan<>(qc, EntityType.TEAM, filter, 7, 8));
      this.assertPlanExists(
          plans,
          new EntityLookup<Team>(qc, EntityType.TEAM, new Manual<>(qc, Set.of(7)), null));
    }
  }

  @Test
  public void teamByTitle() {
    TeamTitleFilter titleFilter = new TeamTitleFilter("Rus", true, false);
    EntityQuery<Team> query = new EntityQuery<>(db, EntityType.TEAM, List.of(titleFilter));

    QueryPlanner planner = new QueryPlanner(db);
    try (var txn = new DatabaseReadTransaction(db)) {
      QueryContext qc = new QueryContext(txn, false);
      List<QueryOperator<Team>> plans = planner.getEntityQueryPlans(qc, query, true);

      this.assertPlanExists(
          plans, new EntityTableScan<>(qc, EntityType.TEAM, titleFilter));
      this.assertPlanExists(
          plans,
          new EntityIndexRangeScan<>(
              qc,
              EntityType.TEAM,
              titleFilter,
              Team.of("Rus"),
              Team.of("Ruszzz"),
              false));
    }
  }

  @Test
  public void teamByGameQuery() {
    EntityQuery<Team> query =
        new EntityQuery<>(
            db,
            EntityType.TEAM,
            List.of(),
            new GameQuery(db, List.of()),
            GameEntityJoinCondition.ANY,
            QuerySortOrder.byTeamDefaultIndex(),
            0);

    try (var txn = new DatabaseReadTransaction(db)) {
      QueryContext qc = new QueryContext(txn, false);
      List<QueryOperator<Team>> plans =
          db.queryPlanner().getEntityQueryPlans(qc, query, true);

      this.assertPlanExists(
          plans,
          new Sort<>(
              qc,
              new EntityLookup<>(
                  qc,
                  EntityType.TEAM,
                  new Distinct<>(
                      qc,
                      new Sort<>(
                          qc,
                          new EntityIdsByGames<Team>(
                              qc, EntityType.TEAM, mockOperator, GameEntityJoinCondition.ANY),
                          QuerySortOrder.byId())),
                  null),
              QuerySortOrder.byTeamDefaultIndex()));
    }
  }

  @Test
  public void multipleSources() {
    TeamTitleFilter titleFilter = new TeamTitleFilter("Rus", true, false);
    EntityQuery<Team> query =
        new EntityQuery<>(
            db,
            EntityType.TEAM,
            List.of(titleFilter),
            new GameQuery(db, List.of()),
            GameEntityJoinCondition.ANY,
            QuerySortOrder.byTeamDefaultIndex(true),
            0);

    doReturn(10000L).when(spyPlanner).entityRangeEstimate(any(), any(), any());

    try (var txn = new DatabaseReadTransaction(db)) {
      QueryContext qc = new QueryContext(txn, false);
      List<QueryOperator<Team>> plans =
          db.queryPlanner().getEntityQueryPlans(qc, query, true);

      QueryOperator<Team> teamsByGamesSub =
          new Distinct<>(
              qc,
              new Sort<>(
                  qc,
                  new EntityIdsByGames<Team>(
                      qc, EntityType.TEAM, mockOperator, GameEntityJoinCondition.ANY),
                  QuerySortOrder.byId()));
      this.assertPlanExists(
          plans,
          new Sort<>(
              qc,
              new EntityLookup<Team>(qc, EntityType.TEAM, teamsByGamesSub, titleFilter),
              QuerySortOrder.byTeamDefaultIndex(true)));

      this.assertPlanExists(
          plans,
          new Sort<>(
              qc,
              new MergeJoin<>(
                  qc,
                  new EntityTableScan<>(qc, EntityType.TEAM, titleFilter),
                  teamsByGamesSub),
              QuerySortOrder.byTeamDefaultIndex(true)));

      QueryOperator<Team> rangeScanOp =
          new EntityIndexRangeScan<>(
              qc,
              EntityType.TEAM,
              titleFilter,
              Team.of("Rus"),
              Team.of("Ruszzz"),
              true);
      this.assertPlanExists(
          plans,
          new Sort<>(
              qc,
              new HashJoin<>(qc, teamsByGamesSub, rangeScanOp),
              QuerySortOrder.byTeamDefaultIndex(true)));

      this.assertPlanExists(plans, new HashJoin<>(qc, rangeScanOp, teamsByGamesSub));
    }
  }

  private void assertPlanExists(
      List<QueryOperator<Team>> plans, QueryOperator<Team> expectedPlan) {
    String expected = expectedPlan.debugString(false);
    for (QueryOperator<Team> plan : plans) {
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

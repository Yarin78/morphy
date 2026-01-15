package se.yarin.morphy.queries;

import org.junit.Before;
import org.junit.Test;
import se.yarin.chess.Date;
import se.yarin.morphy.Database;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.Game;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.entities.Tournament;
import se.yarin.morphy.entities.filters.*;
import se.yarin.morphy.games.filters.DateRangeFilter;
import se.yarin.morphy.queries.operations.*;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

public class TournamentQueryPlanGeneratorTests {
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
  public void tournamentById() {
    ManualFilter<Tournament> tournamentFilter =
        new ManualFilter<>(new int[] {7}, EntityType.TOURNAMENT);
    EntityQuery<Tournament> tq =
        new EntityQuery<Tournament>(db, EntityType.TOURNAMENT, List.of(tournamentFilter));

    try (var txn = new DatabaseReadTransaction(db)) {
      QueryContext qc = new QueryContext(txn, false);
      List<QueryOperator<Tournament>> plans = db.queryPlanner().getEntityQueryPlans(qc, tq, true);

      this.assertPlanExists(
          plans, new EntityTableScan<>(qc, EntityType.TOURNAMENT, tournamentFilter, 7, 8));
      this.assertPlanExists(
          plans,
          new EntityLookup<Tournament>(
              qc, EntityType.TOURNAMENT, new Manual<>(qc, Set.of(7)), null));
    }
  }

  @Test
  public void tournamentsByYearAndPlace() {
    TournamentStartDateFilter f1 = new TournamentStartDateFilter(new Date(1950), Date.unset());
    TournamentPlaceFilter f2 = new TournamentPlaceFilter("London", true, true);
    EntityQuery<Tournament> tournamentQuery =
        new EntityQuery<Tournament>(db, EntityType.TOURNAMENT, List.of(f1, f2));

    EntityFilter<Tournament> combinedFilter = CombinedFilter.combine(List.of(f1, f2));

    try (var txn = new DatabaseReadTransaction(db)) {
      QueryContext qc = new QueryContext(txn, false);
      List<QueryOperator<Tournament>> plans =
          db.queryPlanner().getEntityQueryPlans(qc, tournamentQuery, true);

      this.assertPlanExists(
          plans, new EntityTableScan<>(qc, EntityType.TOURNAMENT, combinedFilter));
      this.assertPlanExists(
          plans,
          new EntityIndexRangeScan<>(
              qc,
              EntityType.TOURNAMENT,
              combinedFilter,
              null,
              Tournament.of("", new Date(1950)),
              false));
    }
  }

  @Test
  public void tournamentsByGames() {
    GameQuery games =
        new GameQuery(db, List.of(new DateRangeFilter(new Date(1900), new Date(2000))));
    TournamentStartDateFilter filter = new TournamentStartDateFilter(new Date(1950), Date.unset());
    EntityQuery<Tournament> tournamentQuery =
        new EntityQuery<Tournament>(db, EntityType.TOURNAMENT, List.of(filter), games, null);

    try (var txn = new DatabaseReadTransaction(db)) {
      QueryContext qc = new QueryContext(txn, false);
      List<QueryOperator<Tournament>> plans =
          db.queryPlanner().getEntityQueryPlans(qc, tournamentQuery, true);

      QueryOperator<Tournament> tournamentsByGamesSub =
          new Distinct<>(
              qc,
              new Sort<>(
                  qc,
                  new EntityIdsByGames<Tournament>(qc, EntityType.TOURNAMENT, mockOperator, null)));
      this.assertPlanExists(
          plans, new EntityLookup<>(qc, EntityType.TOURNAMENT, tournamentsByGamesSub, filter));
      this.assertPlanExists(
          plans,
          new MergeJoin<>(
              qc, new EntityTableScan<>(qc, EntityType.TOURNAMENT, filter), tournamentsByGamesSub));
      this.assertPlanExists(
          plans,
          new HashJoin<>(
              qc,
              new EntityIndexRangeScan<>(
                  qc,
                  EntityType.TOURNAMENT,
                  filter,
                  null,
                  Tournament.of("", new Date(1950)),
                  false),
              tournamentsByGamesSub));
    }
  }

  @Test
  public void multipleSources() {
    TournamentYearTitleFilter yearFilter = new TournamentYearTitleFilter(2024, "Foo", true, false);
    EntityQuery<Tournament> tournamentQuery =
        new EntityQuery<Tournament>(
            db,
            EntityType.TOURNAMENT,
            List.of(yearFilter),
            new GameQuery(db, List.of()),
            null,
            QuerySortOrder.byTournamentDefaultIndex(true),
            0);

    // Ensure the TournamentIndexRangeScan is by default sorted after the TournamentIdsByGames
    // operator
    doReturn(10000L).when(spyPlanner).entityRangeEstimate(any(), any(), any());

    try (var txn = new DatabaseReadTransaction(db)) {
      QueryContext qc = new QueryContext(txn, false);
      List<QueryOperator<Tournament>> plans =
          db.queryPlanner().getEntityQueryPlans(qc, tournamentQuery, true);

      QueryOperator<Tournament> tournamentByGamesSub =
          new Distinct<>(
              qc,
              new Sort<>(
                  qc,
                  new EntityIdsByGames<Tournament>(qc, EntityType.TOURNAMENT, mockOperator, null),
                  QuerySortOrder.byId()));
      this.assertPlanExists(
          plans,
          new Sort<>(
              qc,
              new EntityLookup<>(qc, EntityType.TOURNAMENT, tournamentByGamesSub, yearFilter),
              QuerySortOrder.byTournamentDefaultIndex(true)));

      this.assertPlanExists(
          plans,
          new Sort<>(
              qc,
              new MergeJoin<>(
                  qc,
                  new EntityTableScan<>(qc, EntityType.TOURNAMENT, yearFilter),
                  tournamentByGamesSub),
              QuerySortOrder.byTournamentDefaultIndex(true)));

      QueryOperator<Tournament> rangeScanOp =
          new EntityIndexRangeScan<>(
              qc,
              EntityType.TOURNAMENT,
              yearFilter,
              Tournament.of("Foo", new Date(2024)),
              Tournament.of("Foozzz", new Date(2024)),
              true);
      this.assertPlanExists(
          plans,
          new Sort<>(
              qc,
              new HashJoin<>(qc, tournamentByGamesSub, rangeScanOp),
              QuerySortOrder.byTournamentDefaultIndex(true)));

      this.assertPlanExists(plans, new HashJoin<>(qc, rangeScanOp, tournamentByGamesSub));
    }
  }

  private void showPlans(List<QueryOperator<Tournament>> plans) {
    for (QueryOperator<Tournament> plan : plans) {
      System.out.println(plan.debugString(false));
      System.out.println("---");
    }
  }

  private void assertPlanExists(
      List<QueryOperator<Tournament>> plans, QueryOperator<Tournament> expectedPlan) {
    String expected = expectedPlan.debugString(false);
    for (QueryOperator<Tournament> plan : plans) {
      if (plan.debugString(false).equals(expected)) {
        return;
      }
    }
    throw new AssertionError(
        "Expected plan not found: "
            + expected
            + "\nActual plans: "
            + plans.stream().map(p -> p.debugString(false)).reduce("", (a, b) -> a + "\n" + b));
  }
}

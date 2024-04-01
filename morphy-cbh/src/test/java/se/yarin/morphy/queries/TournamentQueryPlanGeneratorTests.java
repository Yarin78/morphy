package se.yarin.morphy.queries;

import org.junit.Before;
import org.junit.Test;
import se.yarin.chess.Date;
import se.yarin.morphy.Database;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.Game;
import se.yarin.morphy.ResourceLoader;
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
    public void setupTestDb() {
        this.db = ResourceLoader.openWorldChDatabase();

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
        ManualFilter<Tournament> tournamentFilter = new ManualFilter<>(new int[]{7}, EntityType.TOURNAMENT);
        TournamentQuery tq = new TournamentQuery(db, List.of(tournamentFilter));

        try (var txn = new DatabaseReadTransaction(db)) {
            QueryContext qc = new QueryContext(txn, false);
            List<QueryOperator<Tournament>> plans = db.queryPlanner().getTournamentQueryPlans(qc, tq, true);

            this.assertPlanExists(plans, new TournamentTableScan(qc, tournamentFilter, 7, 8));
            this.assertPlanExists(plans, new TournamentLookup(qc, new Manual<>(qc, Set.of(7)), null));
        }
    }

    @Test
    public void tournamentsByYearAndPlace() {
        TournamentStartDateFilter f1 = new TournamentStartDateFilter(new Date(1950), Date.unset());
        TournamentPlaceFilter f2 = new TournamentPlaceFilter("London", true, true);
        TournamentQuery tournamentQuery = new TournamentQuery(db, List.of(f1, f2));

        EntityFilter<Tournament> combinedFilter = CombinedFilter.combine(List.of(f1, f2));

        try (var txn = new DatabaseReadTransaction(db)) {
            QueryContext qc = new QueryContext(txn, false);
            List<QueryOperator<Tournament>> plans = db.queryPlanner().getTournamentQueryPlans(qc, tournamentQuery, true);

            this.assertPlanExists(plans, new TournamentTableScan(qc, combinedFilter));
            this.assertPlanExists(plans, new TournamentIndexRangeScan(qc, combinedFilter,
                    null, Tournament.of("", new Date(1950)), false));
        }
    }

    @Test
    public void tournamentsByGames() {
        GameQuery games = new GameQuery(db, List.of(new DateRangeFilter(new Date(1900), new Date(2000))));
        TournamentStartDateFilter filter = new TournamentStartDateFilter(new Date(1950), Date.unset());
        TournamentQuery tournamentQuery = new TournamentQuery(db, List.of(filter), games);

        try (var txn = new DatabaseReadTransaction(db)) {
            QueryContext qc = new QueryContext(txn, false);
            List<QueryOperator<Tournament>> plans = db.queryPlanner().getTournamentQueryPlans(qc, tournamentQuery, true);

            QueryOperator<Tournament> tournamentsByGamesSub = new Distinct<>(qc, new Sort<>(qc, new TournamentIdsByGames(qc, mockOperator)));
            this.assertPlanExists(plans, new TournamentLookup(qc, tournamentsByGamesSub, filter));
            this.assertPlanExists(plans, new MergeJoin<>(qc, new TournamentTableScan(qc, filter), tournamentsByGamesSub));
            this.assertPlanExists(plans, new HashJoin<>(qc, new TournamentIndexRangeScan(
                    qc, filter, null, Tournament.of("", new Date(1950)),  false), tournamentsByGamesSub));
        }
    }

    @Test
    public void multipleSources() {
        TournamentYearTitleFilter yearFilter = new TournamentYearTitleFilter(2024, "Foo", true, false);
        TournamentQuery tournamentQuery = new TournamentQuery(db, List.of(yearFilter), new GameQuery(db, List.of()), QuerySortOrder.byTournamentDefaultIndex(true), 0);

        // Ensure the TournamentIndexRangeScan is by default sorted after the TournamentIdsByGames operator
        doReturn(10000L).when(spyPlanner).tournamentRangeEstimate(any(), any(), any());

        try (var txn = new DatabaseReadTransaction(db)) {
            QueryContext qc = new QueryContext(txn, false);
            List<QueryOperator<Tournament>> plans = db.queryPlanner().getTournamentQueryPlans(qc, tournamentQuery, true);

            QueryOperator<Tournament> tournamentByGamesSub = new Distinct<>(qc, new Sort<>(qc, new TournamentIdsByGames(qc, mockOperator), QuerySortOrder.byId()));
            this.assertPlanExists(plans,
                    new Sort<>(qc,
                            new TournamentLookup(qc, tournamentByGamesSub, yearFilter),
                            QuerySortOrder.byTournamentDefaultIndex(true)));

            this.assertPlanExists(plans,
                    new Sort<>(qc,
                            new MergeJoin<>(qc, tournamentByGamesSub, new TournamentTableScan(qc, yearFilter)),
                            QuerySortOrder.byTournamentDefaultIndex(true)));

            QueryOperator<Tournament> rangeScanOp = new TournamentIndexRangeScan(qc, yearFilter, Tournament.of("Foo", new Date(2024)), Tournament.of("Foozzz", new Date(2024)), true);
            this.assertPlanExists(plans,
                    new Sort<>(qc,
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

    private void assertPlanExists(List<QueryOperator<Tournament>> plans, QueryOperator<Tournament> expectedPlan) {
        String expected = expectedPlan.debugString(false);
        for (QueryOperator<Tournament> plan : plans) {
            if (plan.debugString(false).equals(expected)) {
                return;
            }
        }
        throw new AssertionError("Expected plan not found: " + expected + "\nActual plans: " + plans.stream().map(p -> p.debugString(false)).reduce("", (a, b) -> a + "\n" + b));
    }
}

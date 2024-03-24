package se.yarin.morphy.queries;

import org.junit.Before;
import org.junit.Test;
import se.yarin.chess.Date;
import se.yarin.morphy.*;
import se.yarin.morphy.entities.Player;
import se.yarin.morphy.entities.Tournament;
import se.yarin.morphy.entities.filters.*;
import se.yarin.morphy.games.filters.DateRangeFilter;
import se.yarin.morphy.games.filters.PlayerFilter;
import se.yarin.morphy.queries.operations.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.*;

public class QueryPlannerTest {
    private Database db;

    @Before
    public void setupTestDb() {
        this.db = ResourceLoader.openWorldChDatabase();
    }

    @Test
    public void simpleGameQuery() {
        GameQuery gameQuery = new GameQuery(db, List.of(new DateRangeFilter(new Date(1950), null)));
        try (var txn = new DatabaseReadTransaction(db)) {
            QueryContext queryContext = new QueryContext(txn, false);
            List<QueryOperator<Game>> plans = db.queryPlanner().getGameQueryPlans(queryContext, gameQuery, true);

            assertEquals(1, plans.size());
            assertEquals("GameTableScan(filter: fromDate >= '1950.??.??')", plans.get(0).toString());
        }
    }

    private <T extends IdObject> void verifyQueryPlans(List<QueryOperator<T>> plans, boolean sorted) {
        // Ensure all query plans return the same result
        List<QueryData<T>> expected = null;
        Map<Integer, QueryData<T>> expectedMap = null;
        for (QueryOperator<T> plan : plans) {
            List<QueryData<T>> planList = plan.stream().collect(Collectors.toList());
            for (QueryData<T> queryData : planList) {
                System.out.println(queryData.id() + " " + queryData.weight());
            }
            System.out.println("---");
            if (expected == null) {
                expected = planList;
                expectedMap = new HashMap<>();
                for (QueryData<T> data : planList) {
                    expectedMap.put(data.id(), data);
                }
                assertTrue(expected.size() > 0);
            } else {
                assertEquals(expected.size(), planList.size());
                if (sorted) {
                    for (int i = 0; i < expected.size(); i++) {
                        assertEquals("Id of element " + i + " differs", expected.get(i).id(), planList.get(i).id());
                        assertEquals("Weight in element " + i + " differs", expected.get(i).weight(), planList.get(i).weight(), 1e-9);
                    }
                } else {
                    for (QueryData<T> planData : planList) {
                        int id = planData.id();
                        QueryData<T> expectedData = expectedMap.get(id);
                        assertNotNull("Element with id " + id + " differs", expectedData);
                        assertEquals("Weight of element with id " + id + " differs", expectedData.weight(), planData.weight(), 1e-9);
                    }
                }
            }
        }
    }

    @Test
    public void gameByEntity() {
        GameQuery gameQuery = new GameQuery(db, List.of(new PlayerFilter(
                db.getPlayer(10), PlayerFilter.PlayerColor.ANY, PlayerFilter.PlayerResult.ANY)),
                null, null, QuerySortOrder.byId(), 0);
        try (var txn = new DatabaseReadTransaction(db)) {
            QueryContext queryContext = new QueryContext(txn, false);
            List<QueryOperator<Game>> plans = db.queryPlanner().getGameQueryPlans(queryContext, gameQuery, true);

            assertTrue(plans.size() >= 2);
            verifyOperators(plans, List.of(GameTableScan.class));
            verifyOperators(plans, List.of(GameIdsByEntities.class));

            verifyQueryPlans(plans, true);
        }
    }

    @Test
    public void gameByPlayerNameAndTournament() {
        PlayerQuery playerQuery = new PlayerQuery(db, List.of(new PlayerNameFilter("K", "", true, false)));
        TournamentQuery tournamentQuery = new TournamentQuery(db, List.of(
                new TournamentStartDateFilter(new Date(1900, 1, 1), new Date(2000, 1, 1))));

        GameQuery gameQuery = new GameQuery(db, null,
                List.of(new GamePlayerJoin(playerQuery, GamePlayerJoinCondition.ANY)),
                List.of(new GameTournamentJoin(tournamentQuery)),
                QuerySortOrder.byId(), 0);

        try (var txn = new DatabaseReadTransaction(db)) {
            QueryContext queryContext = new QueryContext(txn, false);
            List<QueryOperator<Game>> plans = db.queryPlanner().getGameQueryPlans(queryContext, gameQuery, true);

            assertTrue(plans.size() >= 10);
            verifyOperators(plans, List.of(GameTableScan.class));
            verifyOperators(plans, List.of(GameIdsByEntities.class));

            verifyQueryPlans(plans, true);
        }
    }

    @Test
    public void playersByName() {
        PlayerQuery playerQuery = new PlayerQuery(db, List.of(
                new PlayerNameFilter("K", true, false)
        ));

        try (var txn = new DatabaseReadTransaction(db)) {
            QueryContext queryContext = new QueryContext(txn, false);
            List<QueryOperator<Player>> plans = db.queryPlanner().getPlayerQueryPlans(queryContext, playerQuery, true);

            assertTrue(plans.size() >= 2);
            verifyOperators(plans, List.of(PlayerTableScan.class));
            verifyOperators(plans, List.of(PlayerIndexRangeScan.class));

            verifyQueryPlans(plans, false);
        }
    }

    @Test
    public void playersByGames() {
        GameQuery games = new GameQuery(db, List.of(new DateRangeFilter(new Date(1900), new Date(2000))));
        PlayerQuery playerQuery = new PlayerQuery(db, List.of(
                new PlayerNameFilter("K", true, false)
        ), games, GamePlayerJoinCondition.ANY);

        try (var txn = new DatabaseReadTransaction(db)) {
            QueryContext queryContext = new QueryContext(txn, false);
            List<QueryOperator<Player>> plans = db.queryPlanner().getPlayerQueryPlans(queryContext, playerQuery, true);

            assertTrue(plans.size() >= 3);
            verifyOperators(plans, List.of(PlayerTableScan.class));

            verifyQueryPlans(plans, false);
        }
    }

    @Test
    public void tournamentsByYearAndPlace() {
        TournamentQuery tournamentQuery = new TournamentQuery(db, List.of(
                new TournamentStartDateFilter(new Date(1950), Date.unset()),
                new TournamentPlaceFilter("London", true, true)
        ));

        try (var txn = new DatabaseReadTransaction(db)) {
            QueryContext queryContext = new QueryContext(txn, false);
            List<QueryOperator<Tournament>> plans = db.queryPlanner().getTournamentQueryPlans(queryContext, tournamentQuery, true);

            assertTrue(plans.size() >= 2);
            verifyOperators(plans, List.of(TournamentTableScan.class));
            verifyOperators(plans, List.of(TournamentIndexRangeScan.class));

            verifyQueryPlans(plans, false);
        }
    }

    @Test
    public void tournamentsByGames() {
        GameQuery games = new GameQuery(db, List.of(new DateRangeFilter(new Date(1900), new Date(2000))));
        TournamentQuery tournamentQuery = new TournamentQuery(db, List.of(
                new TournamentStartDateFilter(new Date(1950), Date.unset())
        ), games);

        try (var txn = new DatabaseReadTransaction(db)) {
            QueryContext queryContext = new QueryContext(txn, false);
            List<QueryOperator<Tournament>> plans = db.queryPlanner().getTournamentQueryPlans(queryContext, tournamentQuery, true);

            assertTrue(plans.size() >= 2);
            verifyOperators(plans, List.of(TournamentTableScan.class));
            verifyOperators(plans, List.of(TournamentIndexRangeScan.class));

            verifyQueryPlans(plans, false);
        }
    }

    private <T extends IdObject> void verifyOperators(List<QueryOperator<T>> plans, List<Class<?>> expectedClasses) {
        boolean found = false;
        for (QueryOperator<T> plan : plans) {
            List<Class<?>> opClasses = getOperatorSourceClasses(plan).collect(Collectors.toList());
            found |= opClasses.containsAll(expectedClasses);
        }
        assertTrue(found);
    }

    private Stream<Class<?>> getOperatorSourceClasses(QueryOperator<?> op) {
        return Stream.concat(Stream.of(op.getClass()), op.sources().stream().flatMap(this::getOperatorSourceClasses));
    }
}

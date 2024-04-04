package se.yarin.morphy.queries;

import org.junit.Before;
import org.junit.Test;
import se.yarin.morphy.*;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.entities.Player;
import se.yarin.morphy.entities.Tournament;
import se.yarin.morphy.entities.filters.CombinedFilter;
import se.yarin.morphy.entities.filters.ManualFilter;
import se.yarin.morphy.entities.filters.PlayerNameFilter;
import se.yarin.morphy.games.filters.*;
import se.yarin.morphy.queries.operations.*;

import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

public class GameQueryPlanGeneratorTests {
    private Database db;
    private QueryPlanner spyPlanner;
    private QueryOperator<Game> mockOperator;

    @Before
    public void setupTestDb() {
        this.db = ResourceLoader.openWorldChDatabase(); // TODO: Can be empty database?!

        QueryPlanner planner = new QueryPlanner(db);
        this.spyPlanner = spy(planner);
        this.db.setQueryPlanner(this.spyPlanner);
        planner.updatePlanners(this.spyPlanner);

        this.mockOperator = mock(QueryOperator.class);
        when(mockOperator.debugString(anyBoolean())).thenReturn("mock");
        when(mockOperator.getOperatorCost()).thenReturn(ImmutableOperatorCost.builder().build());
        when(mockOperator.hasFullData()).thenReturn(true);

        // doReturn(List.of(mockOperator)).when(spyPlanner).getGameQueryPlans(any(), any(), anyBoolean());
    }

    @Test
    public void allGames() {
        GameQuery gameQuery = new GameQuery(db, null);

        try (var txn = new DatabaseReadTransaction(db)) {
            QueryContext qc = new QueryContext(txn, false);
            List<QueryOperator<Game>> plans = db.queryPlanner().getGameQueryPlans(qc, gameQuery, true);

            assertEquals(1, plans.size());
            this.assertPlanExists(plans, new GameTableScan(qc, null));
        }
    }

    @Test
    public void gamesBySingleFixedPlayer() {
        GameFilter playerFilter = new PlayerFilter(7, PlayerFilter.PlayerColor.WHITE, PlayerFilter.PlayerResult.ANY);
        GameQuery gameQuery = new GameQuery(db, List.of(playerFilter));

        try (var txn = new DatabaseReadTransaction(db)) {
            QueryContext qc = new QueryContext(txn, false);
            List<QueryOperator<Game>> plans = db.queryPlanner().getGameQueryPlans(qc, gameQuery, true);

            this.assertPlanExists(plans, new GameTableScan(qc, playerFilter));
            this.assertPlanExists(plans, new GameLookup(qc, new GameIdsByEntities<>(qc, new Manual<>(qc, Set.of(7)), EntityType.PLAYER), null));
        }

        try (var txn = new DatabaseReadTransaction(db)) {
            QueryContext qc = new QueryContext(txn, false);
            List<QueryOperator<Game>> plans = db.queryPlanner().getGameQueryPlans(qc, gameQuery, false);

            this.assertPlanExists(plans, new GameIdsByEntities<>(qc, new Manual<>(qc, Set.of(7)), EntityType.PLAYER));
        }
    }

    @Test
    public void gamesByMultipleFixedPlayers() {
        GameFilter playerFilter = new PlayerFilter(new int[] {7, 19, 15}, PlayerFilter.PlayerColor.ANY, PlayerFilter.PlayerResult.ANY);
        GameQuery gameQuery = new GameQuery(db, List.of(playerFilter));

        try (var txn = new DatabaseReadTransaction(db)) {
            QueryContext qc = new QueryContext(txn, false);
            List<QueryOperator<Game>> plans = db.queryPlanner().getGameQueryPlans(qc, gameQuery, true);

            this.assertPlanExists(plans, new GameLookup(qc,
                    new Distinct<>(qc, new Sort<>(qc, new GameIdsByEntities<>(qc, new Manual<>(qc, Set.of(7, 15, 19)), EntityType.PLAYER), QuerySortOrder.byId())), null));
        }
    }

    @Test
    public void gamesByFixedPlayerAndRegularGameFilter() {
        GameFilter playerFilter = new PlayerFilter(7, PlayerFilter.PlayerColor.WHITE, PlayerFilter.PlayerResult.ANY);
        GameFilter ratingFilter = new RatingRangeFilter(2500, 2600, RatingRangeFilter.RatingColor.ANY);
        GameQuery gameQuery = new GameQuery(db, List.of(playerFilter, ratingFilter));

        try (var txn = new DatabaseReadTransaction(db)) {
            QueryContext qc = new QueryContext(txn, false);
            List<QueryOperator<Game>> plans = db.queryPlanner().getGameQueryPlans(qc, gameQuery, true);

            GameFilter combinedFilter = CombinedGameFilter.combine(List.of(playerFilter, ratingFilter));
            this.assertPlanExists(plans, new GameTableScan(qc, combinedFilter));
            this.assertPlanExists(plans, new GameLookup(qc, new GameIdsByEntities<>(qc, new Manual<>(qc, Set.of(7)), EntityType.PLAYER), ratingFilter));
            this.assertPlanExists(plans, new MergeJoin<>(qc, new GameIdsByEntities<>(qc, new Manual<>(qc, Set.of(7)), EntityType.PLAYER), new GameTableScan(qc, combinedFilter)));
        }
    }

    @Test
    public void gamesByMultipleFixedEntities() {
        GameFilter playerFilter = new PlayerFilter(7, PlayerFilter.PlayerColor.WHITE, PlayerFilter.PlayerResult.ANY);
        GameFilter tournamentFilter = new TournamentFilter(8);
        GameFilter sourceFilter = new SourceFilter(9);
        GameQuery gameQuery = new GameQuery(db, List.of(playerFilter, tournamentFilter, sourceFilter));

        try (var txn = new DatabaseReadTransaction(db)) {
            QueryContext qc = new QueryContext(txn, false);
            List<QueryOperator<Game>> plans = db.queryPlanner().getGameQueryPlans(qc, gameQuery, true);
            // showPlans(plans);
            // TODO: This causes very many query plans, probably too many
            this.assertPlanExists(plans, new GameLookup(qc,
                new GameIdsByEntities<>(qc, new Manual<>(qc, Set.of(7)), EntityType.PLAYER), CombinedGameFilter.combine(List.of(tournamentFilter, sourceFilter))));
            this.assertPlanExists(plans, new GameLookup(qc,
                new GameIdsByEntities<>(qc, new Manual<>(qc, Set.of(8)), EntityType.TOURNAMENT), CombinedGameFilter.combine(List.of(playerFilter, sourceFilter))));
            this.assertPlanExists(plans, new GameLookup(qc,
                new GameIdsByEntities<>(qc, new Manual<>(qc, Set.of(9)), EntityType.SOURCE), CombinedGameFilter.combine(List.of(playerFilter, tournamentFilter))));

            this.assertPlanExists(plans, new GameLookup(qc,
                    new MergeJoin<>(
                            qc,
                            new GameIdsByEntities<>(qc, new Manual<Tournament>(qc, Set.of(8)), EntityType.TOURNAMENT),
                            new GameIdsByEntities<>(qc, new Manual<Player>(qc, Set.of(7)), EntityType.PLAYER)
                    ), sourceFilter));
        }
    }

    @Test
    public void gamesByEntityFilterAndGameFilter() {
        PlayerNameFilter kasparovFilter = new PlayerNameFilter("Kasparov", true, false);
        EntityQuery<Player> playerQuery = new EntityQuery<>(db, EntityType.PLAYER, List.of(kasparovFilter));
        GameEntityJoin<Player> join = new GameEntityJoin<>(playerQuery, GameQueryJoinCondition.WHITE);
        GameQuery gameQuery = new GameQuery(db, null, List.of(join));
        try (var txn = new DatabaseReadTransaction(db)) {
            QueryContext qc = new QueryContext(txn, false);
            List<QueryOperator<Game>> plans = db.queryPlanner().getGameQueryPlans(qc, gameQuery, true);
            showPlans(plans);
        }
    }

    private <T extends IdObject> void showPlans(List<QueryOperator<T>> plans) {
        for (QueryOperator<T> plan : plans) {
            System.out.println(plan.debugString(false));
            System.out.println("---");
        }
    }

    private <T extends IdObject> void assertPlanExists(List<QueryOperator<T>> plans, QueryOperator<T> expectedPlan) {
        String expected = expectedPlan.debugString(false);
        for (QueryOperator<?> plan : plans) {
            if (plan.debugString(false).equals(expected)) {
                return;
            }
        }
        throw new AssertionError("Expected plan not found: " + expected + "\nActual plans: " + plans.stream().map(p -> p.debugString(false)).reduce("", (a, b) -> a + "\n" + b));
    }
}

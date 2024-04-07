package se.yarin.morphy.queries;

import org.junit.Before;
import org.junit.Test;
import se.yarin.morphy.*;
import se.yarin.morphy.entities.*;
import se.yarin.morphy.entities.filters.AnnotatorNameFilter;
import se.yarin.morphy.entities.filters.PlayerNameFilter;
import se.yarin.morphy.games.filters.*;
import se.yarin.morphy.queries.operations.*;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

public class GameQueryPlanGeneratorTests {
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
        GameFilter playerFilter = new PlayerFilter(7, GameEntityJoinCondition.WHITE);
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
        GameFilter playerFilter = new PlayerFilter(new int[] {7, 19, 15}, null);
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
        GameFilter playerFilter = new PlayerFilter(7, GameEntityJoinCondition.WHITE);
        GameFilter ratingFilter = new RatingRangeFilter(2500, 2600, RatingRangeFilter.RatingColor.ANY);
        GameQuery gameQuery = new GameQuery(db, List.of(playerFilter, ratingFilter));

        try (var txn = new DatabaseReadTransaction(db)) {
            QueryContext qc = new QueryContext(txn, false);
            List<QueryOperator<Game>> plans = db.queryPlanner().getGameQueryPlans(qc, gameQuery, true);

            GameFilter combinedFilter = CombinedGameFilter.combine(List.of(playerFilter, ratingFilter));
            this.assertPlanExists(plans, new GameTableScan(qc, combinedFilter));
            this.assertPlanExists(plans, new GameLookup(qc, new GameIdsByEntities<>(qc, new Manual<>(qc, Set.of(7)), EntityType.PLAYER), ratingFilter));
            this.assertPlanExists(plans, new MergeJoin<>(qc, new GameTableScan(qc, combinedFilter), new GameIdsByEntities<>(qc, new Manual<>(qc, Set.of(7)), EntityType.PLAYER)));
        }
    }

    @Test
    public void gamesByMultipleFixedEntities() {
        GameFilter playerFilter = new PlayerFilter(7, GameEntityJoinCondition.WHITE);
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
                            new GameIdsByEntities<>(qc, new Manual<Player>(qc, Set.of(7)), EntityType.PLAYER),
                            new GameIdsByEntities<>(qc, new Manual<Tournament>(qc, Set.of(8)), EntityType.TOURNAMENT)
                    ), sourceFilter));
        }
    }

    @Test
    public void gamesBySimplePlayerJoin() {
        doReturn(mockOperator).when(spyPlanner).selectBestQueryPlan(anyList());

        PlayerNameFilter kasparovFilter = new PlayerNameFilter("Kasparov", true, false);
        GameEntityJoin<Player> join = new GameEntityJoin<>(new EntityQuery<Player>(db, EntityType.PLAYER, List.of(kasparovFilter)), GameEntityJoinCondition.WHITE);
        GameQuery gameQuery = new GameQuery(db, null, List.of(join));

        try (var txn = new DatabaseReadTransaction(db)) {
            QueryContext qc = new QueryContext(txn, false);
            List<QueryOperator<Game>> plans = db.queryPlanner().getGameQueryPlans(qc, gameQuery, true);

            this.assertPlanExists(plans, new GameEntityLoopJoin<>(qc, new GameTableScan(qc, null), EntityType.PLAYER, kasparovFilter, GameEntityJoinCondition.WHITE));
            this.assertPlanExists(plans, new GameEntityLoopJoin<>(qc, new GameLookup(qc, new Distinct<>(qc, new Sort<>(qc, new GameIdsByEntities<>(qc, mockOperator, EntityType.PLAYER))), null), EntityType.PLAYER, kasparovFilter, GameEntityJoinCondition.WHITE));
        }
    }

    @Test
    public void gamesBySimpleAnnotatorJoinAndComplexTournamentJoin() {
        EntityQuery<Tournament> walkoverTournamentsQuery = new EntityQuery<>(db, EntityType.TOURNAMENT, null, new GameQuery(db, List.of(new ResultsFilter("0-0"))), null);
        EntityQuery<Annotator> annotatorQuery = new EntityQuery<>(db, EntityType.ANNOTATOR, List.of(new AnnotatorNameFilter("foo", true, false)));
        GameQuery gameQuery = new GameQuery(db, null, List.of(new GameEntityJoin<>(annotatorQuery, null), new GameEntityJoin<>(walkoverTournamentsQuery, null)));

        try (var txn = new DatabaseReadTransaction(db)) {
            QueryContext qc = new QueryContext(txn, false);
            List<QueryOperator<Game>> plans = db.queryPlanner().getGameQueryPlans(qc, gameQuery, true);
            //showPlans(plans);
            this.assertPlanExists(plans,
                    new GameEntityHashJoin(qc,
                        new GameEntityLoopJoin<>(qc,
                            new GameTableScan(qc, null), EntityType.ANNOTATOR, new AnnotatorNameFilter("foo", true, false), null), EntityType.TOURNAMENT,
                            new Distinct<>(qc,
                                    new Sort<Tournament>(qc,
                                            new EntityIdsByGames<>(qc, EntityType.TOURNAMENT, new GameTableScan(qc, new ResultsFilter("0-0")), null)
                                    )
                            ),
            null));

            boolean hasHashJoinPlans = false, hasNonHashJoinPlans = false;
            for (QueryOperator<Game> plan : plans) {
                boolean hasHashJoin = false;
                for (var operatorSourceClass : getOperatorSourceClasses(plan).collect(Collectors.toList())) {
                    if (operatorSourceClass.equals(GameEntityHashJoin.class)) {
                        hasHashJoin = true;
                    }
                }
                hasHashJoinPlans |= hasHashJoin;
                hasNonHashJoinPlans |= !hasHashJoin;
            }

            // Since there's only one complex subquery, it should always be possible to construct a plan
            // without hash joins
            assertTrue(hasHashJoinPlans);
            assertTrue(hasNonHashJoinPlans);
        }
    }

    @Test
    public void gamesByTwoComplexJoinsAndGameFilter() {
        EntityQuery<Tournament> walkoverTournamentsQuery = new EntityQuery<>(db, EntityType.TOURNAMENT, null, new GameQuery(db, List.of(new ResultsFilter("0-0"))), null);
        EntityQuery<Player> playersInWorldChQuery = new EntityQuery<>(db, EntityType.PLAYER, null, new GameQuery(db, List.of(new TournamentFilter(1))), null);
        GameQuery gameQuery = new GameQuery(db, null, List.of(new GameEntityJoin<>(playersInWorldChQuery, null), new GameEntityJoin<>(walkoverTournamentsQuery, null)));
        try (var txn = new DatabaseReadTransaction(db)) {
            QueryContext qc = new QueryContext(txn, false);
            List<QueryOperator<Game>> plans = db.queryPlanner().getGameQueryPlans(qc, gameQuery, true);
            // showPlans(plans);
            this.assertPlanExists(plans,
                    new GameEntityHashJoin(qc,
                            new GameEntityHashJoin(qc,
                                    new GameTableScan(qc, null),
                                    EntityType.PLAYER,
                                    new Distinct<>(qc,
                                            new Sort<>(qc,
                                                    new EntityIdsByGames<Player>(qc, EntityType.PLAYER, new GameTableScan(qc, new TournamentFilter(1)), null))),
                                    null
                            ),
                            EntityType.TOURNAMENT,
                            new Distinct<>(qc,
                                    new Sort<Player>(qc,
                                            new EntityIdsByGames<>(qc, EntityType.TOURNAMENT, new GameTableScan(qc, new ResultsFilter("0-0")), null))), null
                            ));
        }
    }


    private <T extends IdObject> void showPlans(List<QueryOperator<T>> plans) {
        for (QueryOperator<T> plan : plans) {
            System.out.println(plan.debugString(false));
            System.out.println("---");
        }
    }

    private Stream<Class<?>> getOperatorSourceClasses(QueryOperator<?> op) {
        return Stream.concat(Stream.of(op.getClass()), op.sources().stream().flatMap(this::getOperatorSourceClasses));
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

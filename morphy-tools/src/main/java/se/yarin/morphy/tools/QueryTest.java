package se.yarin.morphy.tools;

import se.yarin.chess.Date;
import se.yarin.morphy.*;
import se.yarin.morphy.entities.*;
import se.yarin.morphy.entities.filters.*;
import se.yarin.morphy.games.filters.DateRangeFilter;
import se.yarin.morphy.games.filters.IsGameFilter;
import se.yarin.morphy.queries.*;
import se.yarin.morphy.queries.joins.GamePlayerFilterJoin;
import se.yarin.morphy.queries.joins.GameTournamentFilterJoin;
import se.yarin.morphy.queries.operations.*;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class QueryTest {
    private final Database db;

    public QueryTest(Database db) {
        this.db = db;
    }

    public static void main(String[] args) throws IOException {
        Database db = Database.open(new File("/Users/yarin/chess/bases/Mega2021/Mega Database 2021.cbh"), DatabaseMode.READ_ONLY);
        db.queryPlanner().updateStatistics();

        QueryTest queryTest = new QueryTest(db);
        //queryTest.queryCarHighCategoryGames();
        //queryTest.compareCarHighCategoryGames();
        //queryTest.sameMetricQuery();
        //queryTest.carlsenLosesAsWhiteQuery();
        queryTest.playersFrom18thCentury();
    }

    public <T extends IdObject> List<QueryOperator<T>> topQueryPlans(List<QueryOperator<T>> queryPlans, int limit) {
        return topQueryPlans(queryPlans, limit, 100000);
    }

    public <T extends IdObject> List<QueryOperator<T>> topQueryPlans(List<QueryOperator<T>> queryPlans, int limit, int maxCost) {
        return queryPlans
                .stream()
                .sorted(Comparator.comparingLong(o -> (long) o.getQueryCost().estimatedTotalCost()))
                .filter(o -> (long) o.getQueryCost().estimatedTotalCost() < maxCost)
                .limit(limit)
                .collect(Collectors.toList());
    }

    public void queryCarHighCategoryGames() {
        EntityFilter<Tournament> tf1 = new TournamentStartDateFilter(new Date(2000, 1, 1), new Date(3000, 1, 1));
        EntityFilter<Tournament> tf2 = new TournamentCategoryFilter(20, 99);

        EntityFilter<Player> playerFilter = new PlayerNameFilter("Car", "", true, false);

        GameQuery gameQuery = new GameQuery(db, null,
                List.of(
                        new GameEntityJoin<>(new EntityQuery<>(db, EntityType.PLAYER, List.of(playerFilter)), GameQueryJoinCondition.ANY),
                        new GameEntityJoin<>(new EntityQuery<>(db, EntityType.TOURNAMENT, List.of(tf1, tf2)), null)
                ));

        try (var txn = new DatabaseReadTransaction(db)) {
            QueryContext context = new QueryContext(txn, true);

            List<QueryOperator<Game>> queryPlans = db.queryPlanner().getGameQueryPlans(context, gameQuery, true);
            System.out.println(queryPlans.size() + " query plans");
            System.out.println();

            for (QueryOperator<Game> queryPlan : topQueryPlans(queryPlans, 3)) {
                /*
                System.out.println(queryPlan.debugString(false));
                System.out.println(queryPlan.getQueryCost().format());
                System.out.println();

                 */

                detailedQueryExecution(queryPlan);

            }
        }
    }

    public void playersFrom18thCentury() {
        try (var txn = new DatabaseReadTransaction(db)) {
            GameQuery games = new GameQuery(db, List.of(new DateRangeFilter(Date.unset(), new Date(1900, 1, 1))));
            EntityQuery<Player> players = new EntityQuery<Player>(db, EntityType.PLAYER, null, games, GameQueryJoinCondition.ANY);

            QueryContext context = new QueryContext(txn, true);

            List<QueryOperator<Player>> queryPlans = db.queryPlanner().getEntityQueryPlans(context, players, true);
            System.out.println(queryPlans.size() + " query plans");
            System.out.println();

            for (QueryOperator<Player> queryPlan : topQueryPlans(queryPlans, 3)) {
                detailedQueryExecution(queryPlan);
            }
        }
    }

    public void carlsenLosesAsWhiteQuery() {
        try (var txn = new DatabaseReadTransaction(db)) {
            String playerName = "Carlsen, Magnus";
            Player carlsen = db.playerIndex().get(Player.ofFullName(playerName));
            /*
            QueryContext context = new QueryContext(txn, true);
            PlayerIndexRangeScan players = new PlayerIndexRangeScan(context, null, Player.ofFullName(playerName), Player.ofFullName(playerName + "zzz"));
            GameIdsByEntities<Player> gameIds = new GameIdsByEntities<>(context, players, EntityType.PLAYER);
            GameLookup games = new GameLookup(context, gameIds, new PlayerFilter(carlsen, PlayerFilter.PlayerColor.WHITE, PlayerFilter.PlayerResult.LOSS));

            detailedQueryExecution(games);
             */

            GameQuery gameQuery = new GameQuery(db,
                    List.of(new DateRangeFilter(new Date(2000, 1, 1), Date.unset())),
                    List.of(new GameEntityJoin<>(EntityQuery.manual(db, EntityType.PLAYER, List.of(carlsen)), GameQueryJoinCondition.WHITE)));
            QueryContext context = new QueryContext(txn, true);

            List<QueryOperator<Game>> queryPlans = context.queryPlanner().getGameQueryPlans(context, gameQuery, true);
            for (QueryOperator<Game> queryPlan : topQueryPlans(queryPlans, 3)) {
                /*
                System.out.println(queryPlan.getQueryCost().estimatedTotalCost());
                System.out.println(queryPlan.debugString(true));

                 */
                detailedQueryExecution(queryPlan);
                /*
                long numHits = queryPlan.executeProfiled() .stream().count();
                System.out.println("MATCHES " + numHits);

                System.out.println(queryPlan.debugString(true));

                System.out.println();
                 */
            }

        }
    }

    private void sameMetricQuery() {
        try (var txn = new DatabaseReadTransaction(db)) {
            QueryContext context = new QueryContext(txn, true);
            GameTableScan games = new GameTableScan(context, null, 8000000, null);
            TournamentStartDateFilter t1 = new TournamentStartDateFilter(new Date(1950, 1, 1), new Date(2020, 1, 1));
            TournamentTitleFilter t2 = new TournamentTitleFilter("Tata", true, false);

            GameEntityPredicate<Tournament> games1 = new GameEntityPredicate<Tournament>(context, games, EntityType.TOURNAMENT, t1, new GameTournamentFilterJoin(t1));
            GameEntityPredicate<Tournament> games2 = new GameEntityPredicate<Tournament>(context, games1, EntityType.TOURNAMENT, t2, new GameTournamentFilterJoin(t2));

            detailedQueryExecution(games2);
        }
    }


    private void getOldGames() {
        // Get all the games that World Ch contenders during the 19th century have played (not only their World Ch games)
        // Should be Lasker, Steinitz, Chigorin, Gunsberg, Zukertort

        long start = System.currentTimeMillis();
        int startYear = 1800, endYear = 1950;

        CombinedFilter<Tournament> tournamentFilter = new CombinedFilter<>(List.of(
                new TournamentStartDateFilter(new Date(startYear, 1, 1), new Date(endYear, 1, 1)),
                new TournamentTitleFilter("World-ch", true, false)));

        try (var txn = new DatabaseReadTransaction(db)) {
            QueryContext context = new QueryContext(txn, true);
            Tournament startKey = ImmutableTournament.of("", new Date(endYear, 1, 1));
            Tournament endKey = ImmutableTournament.of("", new Date(startYear, 1, 1));
            GameIdsByEntities<Tournament> gameIds = new GameIdsByEntities<>(context, new EntityIndexRangeScan<>(context, EntityType.TOURNAMENT,
                    tournamentFilter, startKey, endKey, false), EntityType.TOURNAMENT);
            GameLookup worldChGames = new GameLookup(context, gameIds, new IsGameFilter());
            QueryOperator<Player> playerIds = new Distinct<>(context, new Sort<>(context, new EntityIdsByGames<Player>(context, EntityType.PLAYER, worldChGames, GameQueryJoinCondition.ANY)));

            //playerIds.stream().forEach(id -> System.out.println(txn.playerTransaction().get(id).lastName()));
            QueryOperator<Game> allGameIds = new Distinct<>(context, new Sort<>(context, new GameIdsByEntities<Player>(context, playerIds, EntityType.PLAYER)));
            GameLookup allGames = new GameLookup(context, allGameIds, new IsGameFilter());

            System.out.println(allGames.stream().limit(200).count());

            System.out.println(allGames.debugString(true));

            txn.metrics().show();
        }

        long stop = System.currentTimeMillis();
        System.out.println((stop - start) + " ms");
    }


    public void compareCarHighCategoryGames() {
        try (var txn = new DatabaseReadTransaction(db)) {
            List<QueryOperator<Game>> queries = List.of(getCarlHighCategoryGames1(txn),
                    getCarlHighCategoryGames2(txn),
                    getCarlHighCategoryGames3(txn));

            for (QueryOperator<Game> query : queries) {
                detailedQueryExecution(query);
            }
        }
    }

    private void detailedQueryExecution(QueryOperator<?> query) {
        IOCPUPerformanceTest.clearPageCache();
        System.out.println("Running query...");
        long numGames = query.executeProfiled().size();
        System.out.println("Query answer: " + numGames);

        System.out.println(query.debugString(true));
        System.out.println();
        System.out.println("TOTAL QUERY COST");
        System.out.println(query.getQueryCost().format());
        // txn.metrics().show();
        System.out.println();
    }

    private QueryOperator<Game> getCarlHighCategoryGames3(DatabaseReadTransaction txn) {
        // 2184
        // 8503841 games iterated, 78528 ms
        String namePrefix = "Car";

        QueryContext context = new QueryContext(txn, true);
        CombinedFilter<Tournament> tournamentFilter = new CombinedFilter<>(List.of(
                new TournamentStartDateFilter(new Date(2000, 1, 1), new Date(3000, 1, 1)),
                new TournamentCategoryFilter(20, 99)
        ));

        PlayerNameFilter playerFilter = new PlayerNameFilter(namePrefix, "", true, false);

        GameTableScan games = new GameTableScan(context, new IsGameFilter());
        GameEntityPredicate<Player> games1 = new GameEntityPredicate<Player>(context, games, EntityType.PLAYER, playerFilter, new GamePlayerFilterJoin(GameQueryJoinCondition.ANY, playerFilter));
        GameEntityPredicate<Tournament> games2 = new GameEntityPredicate<>(context, games1, EntityType.TOURNAMENT, tournamentFilter, new GameTournamentFilterJoin(tournamentFilter));

        return games2;
    }

    private QueryOperator<Game> getCarlHighCategoryGames2(DatabaseReadTransaction txn) {
        //2184
        //11440 games iterated, 476 ms
        String namePrefix = "Car";

        QueryContext context = new QueryContext(txn, true);
        PlayerNameFilter playerNameFilter = new PlayerNameFilter(namePrefix, "", true, false);
        GameEntityPredicate<Player> games = new GameEntityPredicate<Player>(context,
                new GameLookup(context,
                    new GameIdsByEntities<>(context,
                            new EntityTableScan<>(
                                    context,
                                    EntityType.TOURNAMENT,
                                    new CombinedFilter<>(List.of(
                                        new TournamentStartDateFilter(new Date(2000, 1, 1), new Date(3000, 1, 1)),
                                            // new TournamentPlaceFilter("London", true, true),
                                        //new TournamentNationFilter(Set.of(Nation.UNITED_STATES, Nation.SWEDEN, Nation.ENGLAND)),
                                        new TournamentCategoryFilter(20, 99)
                            ))), EntityType.TOURNAMENT),
                    new IsGameFilter()),
                EntityType.PLAYER,
                playerNameFilter, new GamePlayerFilterJoin(GameQueryJoinCondition.ANY, playerNameFilter));

        return games;
    }

    private QueryOperator<Game> getCarlHighCategoryGames1(DatabaseReadTransaction txn) {
        String namePrefix = "Car";

        QueryContext context = new QueryContext(txn, true);
        QueryOperator<Player> playerIndexRangeScan = new EntityIndexRangeScan<>(
                context,
                EntityType.PLAYER,
                new PlayerNameFilter(namePrefix, "", true, false),
                Player.ofFullName(namePrefix), Player.ofFullName(namePrefix + "zzz"), false);

        QueryOperator<Game> gameIds = new GameIdsByEntities<>(context, playerIndexRangeScan, EntityType.PLAYER);

        gameIds = new Distinct<>(context, new Sort<>(context, gameIds));
        QueryOperator<Game> games = new GameLookup(context, gameIds, null);

        CombinedFilter<Tournament> tournamentFilter = new CombinedFilter<>(List.of(
                new TournamentStartDateFilter(new Date(2000, 1, 1), new Date(3000, 1, 1)),
                new TournamentCategoryFilter(20, 99)));
        games = new GameEntityPredicate<>(context, games, EntityType.TOURNAMENT, tournamentFilter, new GameTournamentFilterJoin(tournamentFilter));

        return games;
    }
}

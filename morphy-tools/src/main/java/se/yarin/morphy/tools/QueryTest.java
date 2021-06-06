package se.yarin.morphy.tools;

import se.yarin.chess.Date;
import se.yarin.morphy.Database;
import se.yarin.morphy.DatabaseMode;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.Game;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.entities.ImmutableTournament;
import se.yarin.morphy.entities.Player;
import se.yarin.morphy.entities.Tournament;
import se.yarin.morphy.entities.filters.*;
import se.yarin.morphy.games.filters.IsGameFilter;
import se.yarin.morphy.queries.QueryContext;
import se.yarin.morphy.queries.operations.*;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Random;

public class QueryTest {
    public QueryTest(Database db) {
        this.db = db;
    }

    public static void main(String[] args) throws IOException {
        Database db = Database.open(new File("/Users/yarin/chess/bases/Mega2021/Mega Database 2021.cbh"), DatabaseMode.READ_ONLY);
        db.queryPlanner().updateStatistics();

        QueryTest queryTest = new QueryTest(db);

        queryTest.getCarlHighCategoryGames();
        //queryTest.getCarlHighCategoryGames2();
        //queryTest.getCarlHighCategoryGames3();
        //queryTest.getOldGames();

        //queryTest.performanceTest();
    }

    private final Database db;
    private final Random random = new Random(0);

    private void readBatchedGames(int numBatches, int gamesPerBatch) {
        db.context().instrumentation().reset();
        long start = System.currentTimeMillis();
        try (var txn = new DatabaseReadTransaction(db)) {
            int dummy = 0;
            for (int i = 0; i < numBatches; i++) {
                int firstGame = random.nextInt(db.count() - gamesPerBatch) + 1;
                dummy += txn.stream(firstGame).limit(gamesPerBatch).mapToInt(Game::whitePlayerId).sum();
            }
        }
        long elapsed = System.currentTimeMillis() - start;
        System.err.println("Batch read in " + elapsed + " ms");
        //db.context().instrumentation().show(2);
        System.err.println();
    }

    private void readSeqBatchGames(int numBatches, int gamesPerBatch) {
        db.context().instrumentation().reset();
        long start = System.currentTimeMillis();
        try (var txn = new DatabaseReadTransaction(db)) {
            int dummy = 0;
            for (int i = 0; i < numBatches; i++) {
                int firstGame = random.nextInt(db.count() - gamesPerBatch) + 1;
                for (int j = 0; j < gamesPerBatch; j++) {
                    dummy += txn.getGame(firstGame + j).whitePlayerId();
                }
            }
        }
        long elapsed = System.currentTimeMillis() - start;
        System.err.println("Seq batch read in " + elapsed + " ms");
        //db.context().instrumentation().show(2);
        System.err.println();
    }

    private void readRandomGames(int numGames) {
        db.context().instrumentation().reset();
        long start = System.currentTimeMillis();
        try (var txn = new DatabaseReadTransaction(db)) {
            int dummy = 0;
            for (int i = 0; i < numGames; i++) {
                int gameId = random.nextInt(db.count()) + 1;
                dummy += txn.getGame(gameId).whitePlayerId();
            }
        }
        long elapsed = System.currentTimeMillis() - start;
        System.err.println("Random read in " + elapsed + " ms");
        //db.context().instrumentation().show(2);
        System.err.println();
    }

    private void performanceTest() {
        for (int i = 0; i < 10; i++) {
            //readBatchedGames(10, 999);
            readSeqBatchGames(2500, 20);
            readRandomGames(50000);
        }
    }

    private void getOldGames() {
        // Get all the games that World Ch contenders during the 19th century have played (not only their World Ch games)
        // Should be Lasker, Steinitz, Chigorin, Gunsberg, Zukertort

        long start = System.currentTimeMillis();
        int before = db.context().instrumentation().itemStats("GameHeader").gets();
        int startYear = 1800, endYear = 1950;

        CombinedFilter<Tournament> tournamentFilter = new CombinedFilter<>(List.of(
                new TournamentStartDateFilter(new Date(startYear, 1, 1), new Date(endYear, 1, 1)),
                new TournamentTitleFilter("World-ch", true, false)));

        try (var txn = new DatabaseReadTransaction(db)) {
            QueryContext context = new QueryContext(txn, true);
            Tournament startKey = ImmutableTournament.of("", new Date(endYear, 1, 1));
            Tournament endKey = ImmutableTournament.of("", new Date(startYear, 1, 1));
            GameIdsByEntities<Tournament> gameIds = new GameIdsByEntities<>(context, new TournamentIndexRangeScan(context,
                    tournamentFilter, startKey, endKey), EntityType.TOURNAMENT);
            GameLookup worldChGames = new GameLookup(context, gameIds, new IsGameFilter());
            QueryOperator<Integer> playerIds = new Distinct<>(context, new SortId(context, new PlayerIdsByGames(context, worldChGames)));

            //playerIds.stream().forEach(id -> System.out.println(txn.playerTransaction().get(id).lastName()));
            QueryOperator<Integer> allGameIds = new Distinct<>(context, new SortId(context, new GameIdsByEntityIds(context, playerIds, EntityType.PLAYER)));
            GameLookup allGames = new GameLookup(context, allGameIds, new IsGameFilter());

            System.out.println(allGames.stream().limit(200).count());

            System.out.println(allGames.debugString(true));
        }

        int after = db.context().instrumentation().itemStats("GameHeader").gets();
        long stop = System.currentTimeMillis();
        System.out.println((after - before) + " games deserialized, " + (stop - start) + " ms");
    }

    private void getCarlHighCategoryGames3() {
        long start = System.currentTimeMillis();
        int before = db.context().instrumentation().itemStats("GameHeader").gets();

        // 2184
        // 8503841 games iterated, 78528 ms
        String namePrefix = "Car";

        try (var txn = new DatabaseReadTransaction(db)) {
            QueryContext context = new QueryContext(txn, true);
            CombinedFilter<Tournament> tournamentFilter = new CombinedFilter<>(List.of(
                    new TournamentStartDateFilter(new Date(2000, 1, 1), new Date(3000, 1, 1)),
                    new TournamentCategoryFilter(20, 99)
            ));

            PlayerNameFilter playerFilter = new PlayerNameFilter(namePrefix, "", true, false);

            GameTableScan games = new GameTableScan(context, new IsGameFilter());
            GamePlayerFilter games1 = new GamePlayerFilter(context, games, playerFilter);
            GameTournamentFilter games2 = new GameTournamentFilter(context, games1, tournamentFilter);

            long numGames = games2.stream().count();
            System.out.println(numGames);

            System.out.println(games2.debugString(true));
        }
        int after = db.context().instrumentation().itemStats("GameHeader").gets();
        long stop = System.currentTimeMillis();
        System.out.println((after - before) + " games iterated, " + (stop - start) + " ms");
    }

    private void getCarlHighCategoryGames2() {
        //2184
        //11440 games iterated, 476 ms
        String namePrefix = "Car";

        long start = System.currentTimeMillis();
        int before = db.context().instrumentation().itemStats("GameHeader").gets();
        try (var txn = new DatabaseReadTransaction(db)) {
            QueryContext context = new QueryContext(txn, true);
            GamePlayerFilter games = new GamePlayerFilter(context,
                    new GameLookup(context,
                        new GameIdsByEntities<>(context,
                                new TournamentTableScan(
                                        context,
                                        new CombinedFilter<>(List.of(
                                            new TournamentStartDateFilter(new Date(2000, 1, 1), new Date(3000, 1, 1)),
                                                // new TournamentPlaceFilter("London", true, true),
                                            //new TournamentNationFilter(Set.of(Nation.UNITED_STATES, Nation.SWEDEN, Nation.ENGLAND)),
                                            new TournamentCategoryFilter(20, 99)
                                ))), EntityType.TOURNAMENT),
                        new IsGameFilter()),
                    new PlayerNameFilter(namePrefix, "", true, false));

            long numGames = games.stream().count();
            System.out.println(numGames);

            System.out.println(games.debugString(true));
        }
        int after = db.context().instrumentation().itemStats("GameHeader").gets();
        long stop = System.currentTimeMillis();
        System.out.println((after - before) + " games iterated, " + (stop - start) + " ms");



        //db.context().instrumentation().show(2);
    }

    private void getCarlHighCategoryGames() {
        String namePrefix = "Car";

        long start = System.currentTimeMillis();
        int before = db.context().instrumentation().itemStats("GameHeader").gets();

        try (var txn = new DatabaseReadTransaction(db)) {
            QueryContext context = new QueryContext(txn, true);
            QueryOperator<Player> playerIndexRangeScan = new PlayerIndexRangeScan(
                    context,
                    new PlayerNameFilter(namePrefix, "", true, false),
                    Player.ofFullName(namePrefix), Player.ofFullName(namePrefix + "zzz"));

            QueryOperator<Integer> gameIds = new GameIdsByEntities<>(context, playerIndexRangeScan, EntityType.PLAYER);

            gameIds = new Distinct<>(context, new SortId(context, gameIds));
            QueryOperator<Game> games = new GameLookup(context, gameIds, null);

            CombinedFilter<Tournament> tournamentFilter = new CombinedFilter<>(List.of(
                    new TournamentStartDateFilter(new Date(2000, 1, 1), new Date(3000, 1, 1)),
                    new TournamentCategoryFilter(20, 99)));
            games = new GameTournamentFilter(context, games, tournamentFilter);

            long numGames = games.stream().count();
            System.out.println(numGames);

            System.out.println(games.debugString(true));
        }

        int after = db.context().instrumentation().itemStats("GameHeader").gets();
        long stop = System.currentTimeMillis();
        System.out.println((after - before) + " games iterated, " + (stop - start) + " ms");
    }
}

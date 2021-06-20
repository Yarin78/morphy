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
import se.yarin.util.BlobChannel;
import se.yarin.util.PagedBlobChannel;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
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
        queryTest.compareCarHighCategoryGames();

        //queryTest.compareDeserializations();
        //queryTest.scanVsScatteredRead();
        //queryTest.getOldGames();
        //queryTest.performanceTest();
        //queryTest.rawPageReadInOrder();
        //queryTest.rawPageReadRandom();
    }

    private void compareDeserializations() {
        try (var txn = new DatabaseReadTransaction(db)) {
            Runnable fetchTournaments = () -> {
                txn.getTournament(100);
                txn.getTournament(120);
                txn.getTournament(150);
                txn.getTournament(190);
            };
            Runnable fetchPlayers = () -> {
                txn.getPlayer(100);
                txn.getPlayer(120);
                txn.getPlayer(150);
                txn.getPlayer(190);
            };
            Runnable fetchGames = () -> {
                txn.getGame(100);
                txn.getGame(120);
                txn.getGame(150);
                txn.getGame(190);
            };
            Runnable fetchAnnotators = () -> {
                txn.getAnnotator(100);
                txn.getAnnotator(120);
                txn.getAnnotator(150);
                txn.getAnnotator(190);
            };
            fetchGames.run();
            long start = System.currentTimeMillis();
            for (int i = 0; i < 250000; i++) {
                fetchGames.run();
            }
            System.out.println(System.currentTimeMillis() - start + " ms");
            db.context().instrumentation().getCurrent().show(10);
        }

        // 1,000,000 annotators => 385 ms deserialization
        // 1,000,000 players => 567 ms deserialization
        // 1,000,000 tournaments => 791 ms deserialization
        // 1,000,000 games => 1514 ms deserialization
    }


    private void entityReadTest() {
        try (var txn = new DatabaseReadTransaction(db)) {
            QueryContext context = new QueryContext(txn, true);
            TournamentTableScan tournaments = new TournamentTableScan(context, null);
            PlayerTableScan players = new PlayerTableScan(context, new PlayerNameFilter("", "", true, true), 84545);
            //GameTableScan games = new GameTableScan(context, null, 8000000);
            GameTableScan games = new GameTableScan(context, null, 8503842);
            AnnotatorTableScan annotators = new AnnotatorTableScan(context, null);
            detailedQueryExecution(players);
        }
    }

    private void rawPageReadInOrder() throws IOException {
        clearPageCache();
        BlobChannel channel = BlobChannel.open(new File("/Users/yarin/chess/bases/Mega2021/Mega Database 2021.cbj").toPath());
        long start = System.currentTimeMillis();
        long offset = 0;
        int numPages = 0;

        while (offset < channel.size()) {
            channel.read(offset, PagedBlobChannel.PAGE_SIZE);
            offset += PagedBlobChannel.PAGE_SIZE;
            numPages += 1;
        }
        long elapsed = System.currentTimeMillis() - start;
        System.out.println(numPages + " pages read in " + elapsed + " ms");
    }

    private void rawPageReadRandom() throws IOException {
        clearPageCache();
        Random random = new Random();
        BlobChannel channel = BlobChannel.open(new File("/Users/yarin/chess/bases/Mega2021/Mega Database 2021.cbj").toPath());
        int totalPages = (int) (channel.size() / PagedBlobChannel.PAGE_SIZE);
        int N = 100000;
        long start = System.currentTimeMillis();

        for (int i = 0; i < N; i++) {
            int page = random.nextInt(totalPages);
            channel.read((long) page * PagedBlobChannel.PAGE_SIZE, PagedBlobChannel.PAGE_SIZE);
        }
        long elapsed = System.currentTimeMillis() - start;
        System.out.println(N + " pages read in " + elapsed + " ms");
    }

    private void scanVsScatteredRead() {
        Random random = new Random();
        int N = 340000;
        int[] playerId = new int[N];
        for (int i = 0; i < N; i++) {
            playerId[i] = i;
        }
        // random shuffle
        for (int i = 0; i < N; i++) {
            int t = random.nextInt(N - i);
            int tmp = playerId[i];
            playerId[i] = playerId[i + t];
            playerId[i+t] = tmp;
        }

        try (var txn = new DatabaseReadTransaction(db)) {
            long start = System.currentTimeMillis();
            /*
            for (int i = 0; i < N; i++) {
                txn.getPlayer(playerId[i]);
            }
             */
            txn.playerTransaction().stream().count();
            System.out.println(System.currentTimeMillis() - start + " ms");
            db.context().instrumentation().getCurrent().show(2);
        }
    }

    private final Database db;
    private final Random random = new Random(0);

    private void readBatchedGames(int numBatches, int gamesPerBatch) {
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
        clearPageCache();
        System.out.println("Running query...");
        long numGames = query.executeProfiled().size();
        System.out.println("Query answer: " + numGames);

        System.out.println(query.debugString(true));
        System.out.println();
        System.out.println("Estimated cost: " + query.estimateQueryCost().format());
        System.out.println("Actual cost:    " + query.actualQueryCost().format());
        // txn.metrics().show();
        System.out.println();
    }

    private void clearPageCache() {
        try {
            String[] cmd = {"/bin/bash","-c","sudo -S purge"};
            Process pb = Runtime.getRuntime().exec(cmd);

            String line;
            BufferedReader input = new BufferedReader(new InputStreamReader(pb.getInputStream()));
            while ((line = input.readLine()) != null) {
                System.out.println(line);
            }
            input.close();
            System.out.println("Page cache cleared");
        } catch (IOException e) {
            throw new RuntimeException("Failed to clear page cache");
        }
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
        GamePlayerFilter games1 = new GamePlayerFilter(context, games, playerFilter);
        GameTournamentFilter games2 = new GameTournamentFilter(context, games1, tournamentFilter);

        return games2;
    }

    private QueryOperator<Game> getCarlHighCategoryGames2(DatabaseReadTransaction txn) {
        //2184
        //11440 games iterated, 476 ms
        String namePrefix = "Car";

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

        return games;
    }

    private QueryOperator<Game> getCarlHighCategoryGames1(DatabaseReadTransaction txn) {
        String namePrefix = "Car";

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

        return games;
    }
}

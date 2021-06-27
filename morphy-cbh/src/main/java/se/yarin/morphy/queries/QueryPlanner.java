package se.yarin.morphy.queries;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.cbhlib.games.search.GameIdFilter;
import se.yarin.morphy.Database;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.Game;
import se.yarin.morphy.IdObject;
import se.yarin.morphy.boosters.GameEntityIndex;
import se.yarin.morphy.entities.*;
import se.yarin.morphy.entities.filters.CombinedFilter;
import se.yarin.morphy.entities.filters.EntityFilter;
import se.yarin.morphy.entities.filters.ManualFilter;
import se.yarin.morphy.entities.filters.PlayerNameFilter;
import se.yarin.morphy.exceptions.MorphyNotSupportedException;
import se.yarin.morphy.games.filters.CombinedGameFilter;
import se.yarin.morphy.games.filters.GameFilter;
import se.yarin.morphy.games.filters.PlayerFilter;
import se.yarin.morphy.queries.operations.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class QueryPlanner {
    private static final Logger log = LoggerFactory.getLogger(QueryPlanner.class);

    private final int NUM_SAMPLE_BATCHES = 2500;
    private final int NUM_SAMPLE_ITEMS = 20;

    private final @NotNull Database database;
    private @NotNull StringDistribution playerLastNameDistribution;
    private @NotNull IntBucketDistribution tournamentCategoryDistribution;
    private @NotNull IntBucketDistribution tournamentYearDistribution;
    private @NotNull IntBucketDistribution gamesRatingDistribution;

    public IntBucketDistribution tournamentCategoryDistribution() {
        return tournamentCategoryDistribution;
    }

    public IntBucketDistribution tournamentYearDistribution() {
        return tournamentYearDistribution;
    }

    public QueryPlanner(@NotNull Database database) {
        this.database = database;
        this.playerLastNameDistribution = new StringDistribution(); // TODO: default name distribution
        this.tournamentYearDistribution = new IntBucketDistribution();
        this.tournamentCategoryDistribution = new IntBucketDistribution();
    }

    public List<Game> sampleGames(@NotNull DatabaseReadTransaction txn) {
        int numGames = txn.database().count();
        if (numGames < NUM_SAMPLE_BATCHES * NUM_SAMPLE_ITEMS) {
            return txn.stream().collect(Collectors.toList());
        } else {
            Random random = new Random(0);
            ArrayList<Game> sampleGames = new ArrayList<>();
            for (int i = 0; i < NUM_SAMPLE_BATCHES; i++) {
                int gameId = random.nextInt(numGames);
                for (int j = 0; j < NUM_SAMPLE_ITEMS; j++) {
                    sampleGames.add(txn.getGame(gameId + 1));
                    gameId = (gameId + random.nextInt(10) + 1) % numGames;
                }
            }
            return sampleGames;
        }
    }

    public <T extends Entity & Comparable<T>> List<T> sampleEntities(@NotNull EntityIndexReadTransaction<T> txn) {
        int numEntities = txn.index().count();
        if (numEntities < NUM_SAMPLE_ITEMS * NUM_SAMPLE_ITEMS) {
            return txn.stream().collect(Collectors.toList());
        } else {
            Random random = new Random(0);
            ArrayList<T> sampleEntities = new ArrayList<>();
            for (int i = 0; i < NUM_SAMPLE_BATCHES; i++) {
                int entityId = random.nextInt(numEntities);
                for (int j = 0; j < NUM_SAMPLE_ITEMS; j++) {
                    try {
                        sampleEntities.add(txn.get(entityId));
                    } catch (IllegalArgumentException ignored) {
                        // This is fine, just ignore
                    }
                    entityId = (entityId + random.nextInt(10) + 1) % numEntities;
                }
            }
            return sampleEntities;
        }
    }

    public void updateStatistics() {
        long start = System.currentTimeMillis();
        try (var txn = new DatabaseReadTransaction(database)) {
            List<Game> games = sampleGames(txn);
            List<Player> players = sampleEntities(txn.playerTransaction());
            List<Tournament> tournaments = sampleEntities(txn.tournamentTransaction());

            gamesRatingDistribution = new IntBucketDistribution(3000, games.stream().flatMap(game -> Stream.of(game.whiteElo(), game.blackElo())));

            playerLastNameDistribution = new StringDistribution(players.stream().map(Player::lastName));

            tournamentCategoryDistribution = new IntBucketDistribution(30, tournaments.stream().map(Tournament::category));
            tournamentYearDistribution = new IntBucketDistribution(2100, tournaments.stream().map(tournament -> tournament.date().year()));
        }
        long elapsed = System.currentTimeMillis() - start;
        log.info("Database statistics updated in " + elapsed + " ms");
    }

    /**
     * Gets the ratio of games expected to pass the filter
     * @param gameFilter a filter
     * @return ratio between 0.0 and 1.0 of number of games expected to pass the filter,
     * 0.0 being none and 1.0 being all.
     */
    public double gameFilterEstimate(@Nullable GameFilter gameFilter) {
        // TODO
        return 1.0;
    }

    public double playerFilterEstimate(@Nullable EntityFilter<Player> playerFilter) {
        // TODO
        if (playerFilter instanceof ManualFilter) {
            int count = ((ManualFilter<?>) playerFilter).ids().size();
            return 1.0 * count / database.playerIndex().count();
        }
        return 1.0;
    }

    public double tournamentFilterEstimate(@Nullable EntityFilter<Tournament> tournamentFilter) {
        // TODO
        if (tournamentFilter instanceof ManualFilter) {
            int count = ((ManualFilter<?>) tournamentFilter).ids().size();
            return 1.0 * count / database.tournamentIndex().count();
        }
        return 1.0;
    }


    public double annotatorFilterEstimate(EntityFilter<Annotator> annotatorFilter) {
        // TODO
        return 1.0;
    }

    /**
     * Gets the expected number of players between the two given keys satisfying a filter
     * @param rangeStart start of range
     * @param rangeEnd end of range
     * @param playerFilter an optional additional filter
     * @return number of expected players within range that matches the filter
     */
    public long playerRangeEstimate(@NotNull Player rangeStart, @NotNull Player rangeEnd, @Nullable EntityFilter<Player> playerFilter) {
        double ratio = playerLastNameDistribution.ratioBetween(rangeStart.lastName(), rangeEnd.lastName());
        return Math.max(1, Math.round(database.playerIndex().count() * ratio));
    }

    public long tournamentRangeEstimate(@NotNull Tournament rangeStart, @NotNull Tournament rangeEnd, @Nullable EntityFilter<Tournament> tournamentFilter) {
        return 0;
    }

    public long estimateUniquePages(long numPages, long drawnPages) {
        // https://stats.stackexchange.com/questions/296005/the-expected-number-of-unique-elements-drawn-with-replacement
        return Math.round(numPages*(1-Math.exp(-1.0*drawnPages/numPages)));
    }

    public long estimateTournamentPageReads(long count) {
        long totalTournamentPages = database.entityIndex(EntityType.TOURNAMENT).numDiskPages();

        return estimateUniquePages(totalTournamentPages, count);
    }

    public long estimatePlayerPageReads(long count) {
        long totalPlayerPages = database.entityIndex(EntityType.PLAYER).numDiskPages();

        return estimateUniquePages(totalPlayerPages, count);
    }

    public long estimateGamePageReads(long count) {
        long totalGameHeaderPages = database.gameHeaderIndex().numDiskPages();
        long totalExtendedGameHeaderPages = database.extendedGameHeaderStorage().numDiskPages();

        long expectedGameHeaderPages = estimateUniquePages(totalGameHeaderPages, count);
        long expectedExtendedGameHeaderPages = estimateUniquePages(totalExtendedGameHeaderPages, count);

        return expectedGameHeaderPages + expectedExtendedGameHeaderPages;
    }

    public long estimateGameEntityIndexPageReads(@NotNull EntityType entityType, long count) {
        GameEntityIndex gameEntityIndex = database.gameEntityIndex(entityType);
        assert gameEntityIndex != null;
        long citPages = gameEntityIndex.numTableDiskPages();
        long cibPages = gameEntityIndex.numBlocksDiskPages();

        // We assume here that on average, all games associated with an entity is in the same block in the cib file
        return estimateUniquePages(citPages, count) + estimateUniquePages(cibPages, count);
    }

    public static @NotNull <T extends IdObject> QueryOperator<T> selectBestQueryPlan(@NotNull List<QueryOperator<T>> queryPlans) {
        if (queryPlans.size() == 0) {
            throw new IllegalArgumentException("Need at least one query plan");
        }
        if (queryPlans.size() == 1) {
            return queryPlans.get(0);
        }
        double bestPlanCost = Double.MAX_VALUE;
        QueryOperator<T> bestPlan = queryPlans.get(0);
        for (QueryOperator<T> queryPlan : queryPlans) {
            double totalCost = queryPlan.getQueryCost().estimatedTotalCost();
            if (totalCost < bestPlanCost) {
                bestPlanCost = totalCost;
                bestPlan = queryPlan;
            }
        }
        return bestPlan;
    }

    List<GameSourceQuery> getGameQuerySources(@NotNull QueryContext context, @NotNull GameQuery gameQuery) {
        List<GameSourceQuery> sources = new ArrayList<>();

        sources.add(GameSourceQuery.fromGameQuery(new GameTableScan(context, CombinedGameFilter.combine(gameQuery.gameFilters())), true));

        for (GamePlayerJoin playerJoin : gameQuery.playerJoins()) {
            QueryOperator<Player> playerQueryPlan = selectBestQueryPlan(getPlayerQueryPlans(context, playerJoin.query(), false));
            QueryOperator<Game> gameOp = new Distinct<>(context, new Sort<>(context, new GameIdsByEntities<>(context, playerQueryPlan, EntityType.PLAYER)));
            sources.add(GameSourceQuery.fromGameQuery(gameOp, playerJoin.query().gameQuery() == null));
        }

        for (GameTournamentJoin tournamentJoin : gameQuery.tournamentJoins()) {
            QueryOperator<Tournament> tournamentQueryPlan = selectBestQueryPlan(getTournamentQueryPlans(context, tournamentJoin.query(), false));
            QueryOperator<Game> gameOp = new Distinct<>(context, new Sort<>(context, new GameIdsByEntities<>(context, tournamentQueryPlan, EntityType.TOURNAMENT)));
            sources.add(GameSourceQuery.fromGameQuery(gameOp, tournamentJoin.query().gameQuery() == null));
        }

        for (GameFilter gameFilter : gameQuery.gameFilters()) {
            if (gameFilter instanceof PlayerFilter) {
                PlayerFilter playerFilter = (PlayerFilter) gameFilter;
                QueryOperator<Game> gameOp = new GameIdsByEntities<Player>(context, new Manual<>(context, playerFilter.playerIds()), EntityType.PLAYER);
                if (playerFilter.playerIds().size() > 1) {
                    // If there's only one player id, we are already guaranteed that it will be no duplicates and in order
                    gameOp = new Distinct<>(context, new Sort<>(context, gameOp));
                }
                sources.add(GameSourceQuery.fromGameQuery(gameOp, true));
            }
        }

        return sources;
    }

    List<List<GameSourceQuery>> sourceCombinations(@NotNull List<GameSourceQuery> sources) {
        ArrayList<List<GameSourceQuery>> combinations = new ArrayList<>();
        int n = sources.size();
        for (int i = 0; i < (1<<n); i++) {
            boolean valid = true;
            ArrayList<GameSourceQuery> current = new ArrayList<>();
            for (int j = 0; j < n; j++) {
                if (((1<<j) & i) > 0) {
                    current.add(sources.get(j));
                } else {
                    if (!sources.get(j).isOptional()) {
                        valid = false;
                    }
                }
            }
            if (valid && current.size() > 0) {
                combinations.add(current);
            }
        }
        assert combinations.size() > 0;
        return combinations;
    }

    <T> List<List<T>> generatePermutations(@NotNull List<T> orgEntityFilters) {
        ArrayList<T> entityFilters = new ArrayList<>(orgEntityFilters);
        ArrayList<List<T>> permutations = new ArrayList<>();

        // https://www.baeldung.com/java-array-permutations
        int n = entityFilters.size();
        int[] indexes = new int[n];
        for (int i = 0; i < n; i++) {
            indexes[i] = 0;
        }

        permutations.add(List.copyOf(entityFilters));

        int i = 0;
        while (i < n) {
            if (indexes[i] < i) {
                int j = i % 2 == 0 ?  0: indexes[i];
                T tmp = entityFilters.get(j);
                entityFilters.set(j, entityFilters.get(i));
                entityFilters.set(i, tmp);
                permutations.add(List.copyOf(entityFilters));
                indexes[i]++;
                i = 0;
            }
            else {
                indexes[i] = 0;
                i++;
            }
        }

        return permutations;
    }

    public List<QueryOperator<Tournament>> getTournamentQueryPlans(@NotNull QueryContext context, @NotNull TournamentQuery tournamentQuery, boolean fullData) {
        ArrayList<QueryOperator<Tournament>> queryPlans = new ArrayList<>();

        // Either complete scan and filter
        EntityFilter<Tournament> combinedFilter = CombinedFilter.combine(tournamentQuery.filters());
        queryPlans.add(new TournamentTableScan(context, combinedFilter));

        // Or find an index
        for (EntityFilter<Tournament> filter : tournamentQuery.filters()) {
            // TODO: This should be done nicer
            /*
            // TODO: Statistics not implemented for this filter yet, so skipping it
            if (filter instanceof TournamentStartDateFilter) {
                // Index is in reverse year order
                TournamentStartDateFilter tdFilter = (TournamentStartDateFilter) filter;
                int startYear = tdFilter.fromDate().isUnset() ? 0 : tdFilter.fromDate().year() - 1; // exclusive
                int endYear = tdFilter.toDate().isUnset() ? 9999 : tdFilter.toDate().year();
                queryPlans.add(new TournamentIndexRangeScan(context, combinedFilter,
                        Tournament.of("", new Date(endYear)), Tournament.of("", new Date(startYear))));
            }

             */
            if (filter instanceof ManualFilter) {
                ManualFilter<Tournament> manualFilter = (ManualFilter<Tournament>) filter;
                QueryOperator<Tournament> tournaments = new Manual<>(context, new ArrayList<>(manualFilter.ids()));
                if (fullData) {
                    tournaments = new TournamentLookup(context, tournaments, combinedFilter);
                }
                queryPlans.add(tournaments);
            }
        }

        if (tournamentQuery.gameQuery() == null) {
            return queryPlans;
        }

        // If the tournaments query depends on a game query, things get more complicated
        // This is an additional subquery that must be joined
        // Either merge join in combinations with all other possible query plans
        // or do a "bookmark lookup" with the entire filter
        QueryOperator<Game> gameQueryPlan = selectBestQueryPlan(getGameQueryPlans(context, tournamentQuery.gameQuery(), true));
        QueryOperator<Tournament> complexTournamentQuery = new Distinct<>(context, new Sort<>(context, new TournamentIdsByGames(context, gameQueryPlan)));

        List<QueryOperator<Tournament>> complexQueryPlans = new ArrayList<>();
        for (QueryOperator<Tournament> queryPlan : queryPlans) {
            complexQueryPlans.add(new MergeJoin<>(context, queryPlan, complexTournamentQuery));
        }

        complexQueryPlans.add(new TournamentLookup(context, complexTournamentQuery, combinedFilter));

        return complexQueryPlans;
    }

    public List<QueryOperator<Player>> getPlayerQueryPlans(@NotNull QueryContext context, @NotNull PlayerQuery playerQuery, boolean fullData) {
        ArrayList<QueryOperator<Player>> queryPlans = new ArrayList<>();

        // Either complete scan and filter
        EntityFilter<Player> combinedFilter = CombinedFilter.combine(playerQuery.filters());
        queryPlans.add(new PlayerTableScan(context, combinedFilter));

        // Or find an index
        for (EntityFilter<Player> filter : playerQuery.filters()) {
            // TODO: This should be done nicer
            if (filter instanceof PlayerNameFilter) {
                PlayerNameFilter playerNameFilter = (PlayerNameFilter) filter;
                if (playerNameFilter.isCaseSensitive()) {
                    String lastName = playerNameFilter.lastName();
                    queryPlans.add(new PlayerIndexRangeScan(context, combinedFilter, Player.of(lastName, ""), Player.of(lastName + "zzz", "")));
                }
            }
            if (filter instanceof ManualFilter) {
                ManualFilter<Player> manualFilter = (ManualFilter<Player>) filter;
                QueryOperator<Player> players = new Manual<>(context, new ArrayList<>(manualFilter.ids()));
                if (fullData) {
                    players = new PlayerLookup(context, players, combinedFilter);
                }
                queryPlans.add(players);
            }
        }

        if (playerQuery.gameQuery() == null) {
            return queryPlans;
        }

        // If the player query depends on a game query, things get more complicated
        // This is an additional subquery that must be joined
        // Either merge join in combinations with all other possible query plans
        // or do a "bookmark lookup" with the entire filter
        QueryOperator<Game> gameQueryPlan = selectBestQueryPlan(getGameQueryPlans(context, playerQuery.gameQuery(), true));
        // TODO: join condition
        QueryOperator<Player> complexPlayerQuery = new Distinct<>(context, new Sort<>(context, new PlayerIdsByGames(context, gameQueryPlan)));

        List<QueryOperator<Player>> complexQueryPlans = new ArrayList<>();
        for (QueryOperator<Player> queryPlan : queryPlans) {
            complexQueryPlans.add(new MergeJoin<>(context, queryPlan, complexPlayerQuery));
        }

        complexQueryPlans.add(new PlayerLookup(context, complexPlayerQuery, combinedFilter));

        return complexQueryPlans;
    }

    public List<QueryOperator<Game>> getGameQueryPlans(@NotNull QueryContext context, @NotNull GameQuery gameQuery, boolean fullData) {
        // The anatomy of a GameQuery is like this:
        // We have one or more sources of games to scan from (GameSourceQuery).
        // Each such source yields a stream of either gameId's or Game records (in ascending order, no duplicates).
        // The sourced are joined using a Merge join.
        // If needed a Game lookup is then done, and then the game and entity filters are applied in some order.
        // Applying an entity filter are effectively Loop joins with the entity index.
        //
        // Complex entity queries must be sources (GameIdByEntityIds -> Sort -> Distinct)
        // Game filters can sometimes be a source.
        // Entity filters can sometimes be sources. An Entity filter can either be a perfect source filter
        // (no need to apply the filter later), or only a partial source filter (the entity filter
        // needs to be applied anyway)
        // A full Game table scan is always an optional source (and needed if there are no other sources)

        // 1. Resolve all sources, mandatory and optional

        ArrayList<QueryOperator<Game>> candidateQueryPlans = new ArrayList<>();

        List<GameSourceQuery> sources = getGameQuerySources(context, gameQuery);

        List<GameEntityJoin<?>> gameEntityJoins = gameQuery.entityJoins(true);

        for (List<GameSourceQuery> sourceCombination : sourceCombinations(sources)) {
            // Join the sources starting with the one returning least amount of expected rows
            List<GameSourceQuery> sorted = sourceCombination.stream().sorted(Comparator.comparingLong(GameSourceQuery::estimateRows)).collect(Collectors.toList());

            GameSourceQuery current = sorted.get(0);
            for (int i = 1; i < sorted.size(); i++) {
                current = GameSourceQuery.join(current, sorted.get(i));
            }

            QueryOperator<Game> gameOperator = current.gameOperator();
            // Ensure that full data exists in case we need to do additional entity filtering, or full data is required
            if (!gameOperator.hasFullData() && (gameEntityJoins.size() > 0 || fullData)) {
                gameOperator = new GameLookup(context, current.gameOperator(), CombinedGameFilter.combine(gameQuery.gameFilters()));
            }

            for (List<GameEntityJoin<?>> entityJoinPermutation : generatePermutations(gameEntityJoins)) {
                QueryOperator<Game> currentGameOperator = gameOperator;
                for (GameEntityJoin<?> entityJoin : entityJoinPermutation) {
                    currentGameOperator = entityJoin.applyGameFilter(context, currentGameOperator);
                }
                candidateQueryPlans.add(currentGameOperator);
            }
        }

        return candidateQueryPlans;

    }

}

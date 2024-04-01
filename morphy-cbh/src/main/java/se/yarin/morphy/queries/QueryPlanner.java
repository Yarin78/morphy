package se.yarin.morphy.queries;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.chess.Date;
import se.yarin.morphy.Database;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.Game;
import se.yarin.morphy.IdObject;
import se.yarin.morphy.boosters.GameEntityIndex;
import se.yarin.morphy.entities.*;
import se.yarin.morphy.entities.filters.*;
import se.yarin.morphy.games.filters.CombinedGameFilter;
import se.yarin.morphy.games.filters.GameFilter;
import se.yarin.morphy.games.filters.PlayerFilter;
import se.yarin.morphy.games.filters.TournamentFilter;
import se.yarin.morphy.queries.operations.*;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static se.yarin.util.Collections.generatePermutations;

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
    public long playerRangeEstimate(@Nullable Player rangeStart, @Nullable Player rangeEnd, @Nullable EntityFilter<Player> playerFilter) {
        double ratio = playerLastNameDistribution.ratioBetween(rangeStart == null ? "" : rangeStart.lastName(), rangeEnd == null ? "zzz" : rangeEnd.lastName());
        return Math.max(1, Math.round(database.playerIndex().count() * ratio));
    }

    public long tournamentRangeEstimate(@Nullable Tournament rangeStart, @Nullable Tournament rangeEnd, @Nullable EntityFilter<Tournament> tournamentFilter) {
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
        if (queryPlans.isEmpty()) {
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

        sources.add(GameSourceQuery.fromGameQueryOperator(new GameTableScan(context, CombinedGameFilter.combine(gameQuery.gameFilters())), true, gameQuery.gameFilters()));

        for (GamePlayerJoin playerJoin : gameQuery.playerJoins()) {
            QueryOperator<Player> playerQueryPlan = selectBestQueryPlan(getPlayerQueryPlans(context, playerJoin.query(), false));
            QueryOperator<Game> gameOp = new Distinct<>(context, new Sort<>(context, new GameIdsByEntities<>(context, playerQueryPlan, EntityType.PLAYER)));
            sources.add(GameSourceQuery.fromGameQueryOperator(gameOp, playerJoin.isSimpleJoin(), List.of()));
        }

        for (GameTournamentJoin tournamentJoin : gameQuery.tournamentJoins()) {
            QueryOperator<Tournament> tournamentQueryPlan = selectBestQueryPlan(getTournamentQueryPlans(context, tournamentJoin.query(), false));
            QueryOperator<Game> gameOp = new Distinct<>(context, new Sort<>(context, new GameIdsByEntities<>(context, tournamentQueryPlan, EntityType.TOURNAMENT)));
            sources.add(GameSourceQuery.fromGameQueryOperator(gameOp, tournamentJoin.isSimpleJoin(), List.of()));
        }

        for (GameFilter gameFilter : gameQuery.gameFilters()) {
            if (gameFilter instanceof PlayerFilter) {
                PlayerFilter playerFilter = (PlayerFilter) gameFilter;
                QueryOperator<Game> gameOp = new GameIdsByEntities<Player>(context, new Manual<>(context, Set.copyOf(playerFilter.playerIds())), EntityType.PLAYER);

                gameOp = new Distinct<>(context, new Sort<>(context, gameOp));
                sources.add(GameSourceQuery.fromGameQueryOperator(gameOp, true, List.of(gameFilter)));
            }
            if (gameFilter instanceof TournamentFilter) {
                TournamentFilter tournamentFilter = (TournamentFilter) gameFilter;
                QueryOperator<Game> gameOp = new GameIdsByEntities<Tournament>(context, new Manual<>(context, Set.copyOf(tournamentFilter.tournamentIds())), EntityType.TOURNAMENT);

                gameOp = new Distinct<>(context, new Sort<>(context, gameOp));
                sources.add(GameSourceQuery.fromGameQueryOperator(gameOp, true, List.of(gameFilter)));
            }
        }

        return sources;
    }

    List<EntitySourceQuery<Player>> getPlayerQuerySources(@NotNull QueryContext context, @NotNull PlayerQuery playerQuery) {
        ArrayList<EntitySourceQuery<Player>> sources = new ArrayList<>();

        EntityFilter<Player> combinedFilter = CombinedFilter.combine(playerQuery.filters());

        Integer startId = null, endId = null;
        Player startEntity = null, endEntity = null;

        for (EntityFilter<Player> filter : playerQuery.filters()) {
            if (filter instanceof ManualFilter) {
                ManualFilter<Player> manualFilter = (ManualFilter<Player>) filter;
                QueryOperator<Player> players = new Manual<>(context, Set.copyOf(manualFilter.ids()));
                sources.add(EntitySourceQuery.fromQueryOperator(players, true, List.of(filter)));
                int minId = manualFilter.minId(), maxId = manualFilter.maxId() + 1; // endId is exclusive
                if (startId == null || minId > startId)
                    startId = minId;
                if (endId == null || maxId < endId)
                    endId = maxId;
            }
            if (filter instanceof EntityIndexFilter) {
                EntityIndexFilter<Player> indexFilter = (EntityIndexFilter<Player>) filter;
                Player p = indexFilter.start();
                if (p != null && (startEntity == null || p.compareTo(startEntity) > 0))
                    startEntity = p;
                p = indexFilter.end();
                if (p != null && (endEntity == null || p.compareTo(endEntity) < 0))
                    endEntity = p;
            }
        }

        sources.add(EntitySourceQuery.fromQueryOperator(new PlayerTableScan(context, combinedFilter, startId, endId), true, playerQuery.filters()));

        boolean reverseNameOrder = playerQuery.sortOrder().isSameOrStronger(QuerySortOrder.byPlayerDefaultIndex(true));
        sources.add(EntitySourceQuery.fromQueryOperator(new PlayerIndexRangeScan(context, combinedFilter, startEntity, endEntity, reverseNameOrder), true, playerQuery.filters()));

        GameQuery gameQuery = playerQuery.gameQuery();
        if (gameQuery != null) {
            QueryOperator<Game> gameQueryPlan = selectBestQueryPlan(getGameQueryPlans(context, gameQuery, true));
            GamePlayerJoinCondition joinCondition = playerQuery.joinCondition();
            assert joinCondition != null;
            QueryOperator<Player> complexPlayerQuery = new Distinct<>(context, new Sort<>(context, new PlayerIdsByGames(context, gameQueryPlan, joinCondition)));
            sources.add(EntitySourceQuery.fromQueryOperator(complexPlayerQuery, false, List.of()));
        }

        return sources;
    }

    List<EntitySourceQuery<Tournament>> getTournamentQuerySources(@NotNull QueryContext context, @NotNull TournamentQuery tournamentQuery) {
        ArrayList<EntitySourceQuery<Tournament>> sources = new ArrayList<>();

        EntityFilter<Tournament> combinedFilter = CombinedFilter.combine(tournamentQuery.filters());

        Integer startId = null, endId = null;
        Tournament startEntity = null, endEntity = null;

        for (EntityFilter<Tournament> filter : tournamentQuery.filters()) {
            if (filter instanceof ManualFilter) {
                ManualFilter<Tournament> manualFilter = (ManualFilter<Tournament>) filter;
                QueryOperator<Tournament> tournaments = new Manual<>(context, Set.copyOf(manualFilter.ids()));
                sources.add(EntitySourceQuery.fromQueryOperator(tournaments, true, List.of(filter)));
                int minId = manualFilter.minId(), maxId = manualFilter.maxId() + 1; // endId is exclusive
                if (startId == null || minId > startId)
                    startId = minId;
                if (endId == null || maxId < endId)
                    endId = maxId;
            }
            if (filter instanceof EntityIndexFilter) {
                EntityIndexFilter<Tournament> indexFilter = (EntityIndexFilter<Tournament>) filter;
                Tournament p = indexFilter.start();
                if (p != null && (startEntity == null || p.compareTo(startEntity) > 0))
                    startEntity = p;
                p = indexFilter.end();
                if (p != null && (endEntity == null || p.compareTo(endEntity) < 0))
                    endEntity = p;
            }
        }

        sources.add(EntitySourceQuery.fromQueryOperator(new TournamentTableScan(context, combinedFilter, startId, endId), true, tournamentQuery.filters()));

        boolean reverseNameOrder = tournamentQuery.sortOrder().isSameOrStronger(QuerySortOrder.byTournamentDefaultIndex(true));
        sources.add(EntitySourceQuery.fromQueryOperator(new TournamentIndexRangeScan(context, combinedFilter, startEntity, endEntity, reverseNameOrder), true, tournamentQuery.filters()));

        GameQuery gameQuery = tournamentQuery.gameQuery();
        if (gameQuery != null) {
            QueryOperator<Game> gameQueryPlan = selectBestQueryPlan(getGameQueryPlans(context, gameQuery, true));
            // GamePlayerJoinCondition joinCondition = tournamentQuery.joinCondition();
            // assert joinCondition != null;
            QueryOperator<Tournament> complexPlayerQuery = new Distinct<>(context, new Sort<>(context, new TournamentIdsByGames(context, gameQueryPlan)));
            sources.add(EntitySourceQuery.fromQueryOperator(complexPlayerQuery, false, List.of()));
        }

        return sources;
    }

    <T extends SourceQuery<?>> List<List<T>> sourceCombinations(@NotNull List<T> sources) {
        ArrayList<List<T>> combinations = new ArrayList<>();
        int n = sources.size();
        for (int i = 0; i < (1<<n); i++) {
            HashSet<Object> seenFiltersCovered = new HashSet<>();
            boolean valid = true;
            ArrayList<T> current = new ArrayList<>();
            for (int j = 0; j < n; j++) {
                if (((1<<j) & i) > 0) {
                    current.add(sources.get(j));
                    if (!seenFiltersCovered.add(sources.get(j).filtersCovered()) && sources.get(j).isOptional()) {
                        // Unnecessary to have this optional source since it's filters were already covered by another source
                        valid = false;
                    }
                } else {
                    if (!sources.get(j).isOptional()) {
                        valid = false;
                    }
                }
            }
            if (valid && !current.isEmpty()) {
                combinations.add(current);
            }
        }
        assert !combinations.isEmpty();
        return combinations;
    }



    public List<QueryOperator<Tournament>> getTournamentQueryPlans(@NotNull QueryContext context, @NotNull TournamentQuery tournamentQuery, boolean fullData) {
        List<QueryOperator<Tournament>> candidateQueryPlans = new ArrayList<>();

        List<EntitySourceQuery<Tournament>> sources = getTournamentQuerySources(context, tournamentQuery);

        for (List<EntitySourceQuery<Tournament>> sourceCombination : sourceCombinations(sources)) {
            // Join the sources starting with the one returning least amount of expected rows
            List<EntitySourceQuery<Tournament>> sortedByEstimatedRows = sourceCombination.stream().sorted(Comparator.comparingLong(EntitySourceQuery::estimateRows)).collect(Collectors.toList());

            candidateQueryPlans.add(getFinalTournamentQueryOperator(tournamentQuery, sortedByEstimatedRows, fullData));

            if (!tournamentQuery.sortOrder().isNone()) {
                // We also want to check if one of the source queries already has the data in the right order.
                // If so, by starting with that one and the applying the other sources in order we might end up with
                // a better result as we don't need the final sort.
                for (int i = 1; i < sortedByEstimatedRows.size(); i++) { // i=0 is already covered above
                    EntitySourceQuery<Tournament> sourceQuery = sortedByEstimatedRows.get(i);
                    if (sourceQuery.operator().sortOrder().isSameOrStronger(tournamentQuery.sortOrder())) {
                        List<EntitySourceQuery<Tournament>> alternateOrder = new ArrayList<>(sortedByEstimatedRows);
                        alternateOrder.remove(sourceQuery);
                        alternateOrder.add(0, sourceQuery);
                        candidateQueryPlans.add(getFinalTournamentQueryOperator(tournamentQuery, alternateOrder, fullData));
                    }
                }
            }
        }

        return candidateQueryPlans;
    }

    private static QueryOperator<Tournament> getFinalTournamentQueryOperator(@NotNull TournamentQuery tournamentQuery, @NotNull List<EntitySourceQuery<Tournament>> sources, boolean fullData) {
        boolean fullDataRequired = fullData;
        QuerySortOrder<Tournament> sortOrder = tournamentQuery.sortOrder();
        if (sortOrder.requiresData()) {
            fullDataRequired = true;
        }

        EntitySourceQuery<Tournament> current = sources.get(0);
        for (int i = 1; i < sources.size(); i++) {
            current = EntitySourceQuery.join(current, sources.get(i));
        }

        List<EntityFilter<Tournament>> filtersLeft = new ArrayList<>(tournamentQuery.filters());
        filtersLeft.removeAll(current.filtersCovered());

        QueryOperator<Tournament> operator = current.operator();
        if (!filtersLeft.isEmpty() || (!operator.hasFullData() && fullDataRequired)) {
            operator = new TournamentLookup(current.context(), operator, CombinedFilter.combine(filtersLeft));
        }

        return operator.sortedAndDistinct(sortOrder, tournamentQuery.limit());
    }

    public List<QueryOperator<Player>> getPlayerQueryPlans(@NotNull QueryContext context, @NotNull PlayerQuery playerQuery, boolean fullData) {
        List<QueryOperator<Player>> candidateQueryPlans = new ArrayList<>();

        List<EntitySourceQuery<Player>> sources = getPlayerQuerySources(context, playerQuery);

        for (List<EntitySourceQuery<Player>> sourceCombination : sourceCombinations(sources)) {
            // Join the sources starting with the one returning least amount of expected rows
            List<EntitySourceQuery<Player>> sortedByEstimatedRows = sourceCombination.stream().sorted(Comparator.comparingLong(EntitySourceQuery::estimateRows)).collect(Collectors.toList());

            candidateQueryPlans.add(getFinalPlayerQueryOperator(playerQuery, sortedByEstimatedRows, fullData));

            if (!playerQuery.sortOrder().isNone()) {
                // We also want to check if one of the source queries already has the data in the right order.
                // If so, by starting with that one and the applying the other sources in order we might end up with
                // a better result as we don't need the final sort.
                for (int i = 1; i < sortedByEstimatedRows.size(); i++) { // i=0 is already covered above
                    EntitySourceQuery<Player> sourceQuery = sortedByEstimatedRows.get(i);
                    if (sourceQuery.operator().sortOrder().isSameOrStronger(playerQuery.sortOrder())) {
                        List<EntitySourceQuery<Player>> alternateOrder = new ArrayList<>(sortedByEstimatedRows);
                        alternateOrder.remove(sourceQuery);
                        alternateOrder.add(0, sourceQuery);
                        candidateQueryPlans.add(getFinalPlayerQueryOperator(playerQuery, alternateOrder, fullData));
                    }
                }
            }
        }

        return candidateQueryPlans;
    }

    private static QueryOperator<Player> getFinalPlayerQueryOperator(@NotNull PlayerQuery playerQuery, @NotNull List<EntitySourceQuery<Player>> sources, boolean fullData) {
        boolean fullDataRequired = fullData;
        QuerySortOrder<Player> sortOrder = playerQuery.sortOrder();
        if (sortOrder.requiresData()) {
            fullDataRequired = true;
        }

        EntitySourceQuery<Player> current = sources.get(0);
        for (int i = 1; i < sources.size(); i++) {
            current = EntitySourceQuery.join(current, sources.get(i));
        }

        List<EntityFilter<Player>> filtersLeft = new ArrayList<>(playerQuery.filters());
        filtersLeft.removeAll(current.filtersCovered());

        QueryOperator<Player> playerOperator = current.operator();
        if (!filtersLeft.isEmpty() || (!playerOperator.hasFullData() && fullDataRequired)) {
            playerOperator = new PlayerLookup(current.context(), playerOperator, CombinedFilter.combine(filtersLeft));
        }

        return playerOperator.sortedAndDistinct(sortOrder, playerQuery.limit());
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

        List<GameEntityJoin<?>> simpleEntityJoins = gameQuery.entityJoins(true);

        boolean fullDataRequired = !simpleEntityJoins.isEmpty() || fullData;
        // TODO: If a simple entity join is used as a game source, full data might still not be needed!?
        // TODO: Shouldn't be null since we have QuerySortOrder.none()!?
        QuerySortOrder<Game> sortOrder = gameQuery.sortOrder();
        if (sortOrder.requiresData()) {
            fullDataRequired = true;
        }

        for (List<GameSourceQuery> sourceCombination : sourceCombinations(sources)) {
            // Join the sources starting with the one returning least amount of expected rows
            List<GameSourceQuery> sorted = sourceCombination.stream().sorted(Comparator.comparingLong(GameSourceQuery::estimateRows)).collect(Collectors.toList());

            GameSourceQuery current = sorted.get(0);
            for (int i = 1; i < sorted.size(); i++) {
                current = GameSourceQuery.join(current, sorted.get(i));
            }

            QueryOperator<Game> gameOperator = current.gameOperator();
            // Ensure that full data exists in case we need to do additional entity filtering/sorting, or full data is required
            if (!gameOperator.hasFullData() && fullDataRequired) {
                // TODO: Are we sure that if we don't get here, that we've applied the gameQuery.gameFilters?!
                gameOperator = new GameLookup(context, current.gameOperator(), CombinedGameFilter.combine(gameQuery.gameFilters()));
            }

            for (List<GameEntityJoin<?>> entityJoinPermutation : generatePermutations(simpleEntityJoins)) {
                QueryOperator<Game> currentGameOperator = gameOperator;
                // TODO: If the entityJoin was a source, there's no need to apply it again
                for (GameEntityJoin<?> entityJoin : entityJoinPermutation) {
                    currentGameOperator = entityJoin.applyGameFilter(context, currentGameOperator);
                }

                currentGameOperator = currentGameOperator.sortedAndDistinct(sortOrder, gameQuery.limit());

                candidateQueryPlans.add(currentGameOperator);
            }
        }

        return candidateQueryPlans;
    }

}


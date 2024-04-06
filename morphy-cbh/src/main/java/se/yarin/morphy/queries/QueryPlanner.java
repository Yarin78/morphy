package se.yarin.morphy.queries;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.morphy.Database;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.Game;
import se.yarin.morphy.IdObject;
import se.yarin.morphy.boosters.GameEntityIndex;
import se.yarin.morphy.entities.*;
import se.yarin.morphy.entities.filters.*;
import se.yarin.morphy.entities.filters.ManualFilter;
import se.yarin.morphy.games.filters.*;
import se.yarin.morphy.queries.operations.*;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static se.yarin.util.Collections.generatePermutations;

public class QueryPlanner {
    private static final Logger log = LoggerFactory.getLogger(QueryPlanner.class);

    private final int NUM_SAMPLE_BATCHES = 2500;
    private final int NUM_SAMPLE_ITEMS = 20;

    private final @NotNull EntityQueryPlanner entityQueryPlanner;

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
        this.entityQueryPlanner = new EntityQueryPlanner(this);

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
     *
     * @param gameFilter a filter
     * @return ratio between 0.0 and 1.0 of number of games expected to pass the filter,
     * 0.0 being none and 1.0 being all.
     */
    public double gameFilterEstimate(@Nullable GameFilter gameFilter) {
        // TODO
        return 1.0;
    }

    public <T extends Entity & Comparable<T>> double entityFilterEstimate(@Nullable EntityFilter<T> entityFilter, EntityType entityType) {
        // TODO
        if (entityFilter instanceof ManualFilter) {
            int count = ((ManualFilter<?>) entityFilter).ids().size();
            return 1.0 * count / database.entityIndex(entityType).count();
        }
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
     *
     * @param rangeStart   start of range
     * @param rangeEnd     end of range
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

    public <T extends Entity & Comparable<T>> long entityRangeEstimate(@Nullable T rangeStart, @Nullable T rangeEnd, @Nullable EntityFilter<T> entityFilter) {
        // TODO: Make generic
        return 1;
    }

    public long estimateUniquePages(long numPages, long drawnPages) {
        // https://stats.stackexchange.com/questions/296005/the-expected-number-of-unique-elements-drawn-with-replacement
        return Math.round(numPages * (1 - Math.exp(-1.0 * drawnPages / numPages)));
    }

    public long estimateTournamentPageReads(long count) {
        long totalTournamentPages = database.entityIndex(EntityType.TOURNAMENT).numDiskPages();

        return estimateUniquePages(totalTournamentPages, count);
    }

    public long estimatePlayerPageReads(long count) {
        long totalPlayerPages = database.entityIndex(EntityType.PLAYER).numDiskPages();

        return estimateUniquePages(totalPlayerPages, count);
    }

    public long estimateEntityPageReads(long count, EntityType entityType) {
        long totalPlayerPages = database.entityIndex(entityType).numDiskPages();

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

    public @NotNull <T extends IdObject> QueryOperator<T> selectBestQueryPlan(@NotNull List<QueryOperator<T>> queryPlans) {
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

        QuerySortOrder<Game> sortOrder = gameQuery.sortOrder();

        for (List<GameSourceQuery> sourceCombination : sourceCombinations(sources)) {
            // Join the sources starting with the one returning least amount of expected rows
            List<GameSourceQuery> sorted = sourceCombination.stream().sorted(Comparator.comparingLong(GameSourceQuery::estimateRows)).collect(Collectors.toList());

            GameSourceQuery current = sorted.get(0);
            for (int i = 1; i < sorted.size(); i++) {
                current = GameSourceQuery.join(current, sorted.get(i));
            }
            QueryOperator<Game> gameOperator = current.gameOperator();

            List<GameFilter> filtersLeft = new ArrayList<>(gameQuery.gameFilters());
            filtersLeft.removeAll(current.filtersCovered());
            List<GameEntityJoin<?>> entityJoinsLeft = new ArrayList<>(gameQuery.entityJoins());
            entityJoinsLeft.removeAll(current.entityJoinsCovered());

            // Ensure that full data exists in case we need to do additional filtering/sorting, or full data is required
            boolean fullDataRequired = fullData;
            if (sortOrder.requiresData() || !entityJoinsLeft.isEmpty()) {
                fullDataRequired = true;
            }
            if ((!gameOperator.hasFullData() && fullDataRequired) || !filtersLeft.isEmpty()) {
                // Note: If we have to this lookup, perhaps it's best to always use all game filters from
                // the query instead of only filtersLeft? In theory we shouldn't, but if the entity indexes
                // are broken, we could end up showing wrong data very needlessly.
                gameOperator = new GameLookup(context, current.gameOperator(), CombinedGameFilter.combine(filtersLeft));
            }

            for (var entityJoinPermutation : generatePermutations(entityJoinsLeft)) {
                QueryOperator<Game> currentGameOperator = gameOperator;
                for (var entityJoin : entityJoinPermutation) {
                    if (entityJoin.isSimpleJoin()) {
                        currentGameOperator = entityJoin.loopJoin(context, currentGameOperator);
                    } else {
                        var entityQueryOperator = selectBestQueryPlan(getEntityQueryPlans(context, entityJoin.entityQuery(), false));
                        currentGameOperator = new GameEntityHashJoin(context, currentGameOperator, entityJoin.getEntityType(), entityQueryOperator, entityJoin.joinCondition());
                    }
                }
                candidateQueryPlans.add(currentGameOperator.sortedAndDistinct(sortOrder, gameQuery.limit()));
            }
        }

        return candidateQueryPlans;
    }



    List<GameSourceQuery> getGameQuerySources(@NotNull QueryContext context, @NotNull GameQuery gameQuery) {
        List<GameSourceQuery> sources = new ArrayList<>();

        // TODO: Investigate if a full GameTableScan is ever the right thing in case any other source is available
        // Any entity index + game lookup should almost always be better
        sources.add(GameSourceQuery.fromGameQueryOperator(new GameTableScan(context, CombinedGameFilter.combine(gameQuery.gameFilters())), true, gameQuery.gameFilters(), List.of()));

        for (GameEntityJoin<?> entityJoin : gameQuery.entityJoins()) {
            QueryOperator<?> entityQueryPlan = selectBestQueryPlan(entityQueryPlanner.getQueryPlans(context, entityJoin.entityQuery(), false));
            QueryOperator<Game> gameOp = new GameIdsByEntities<>(context, entityQueryPlan, entityJoin.getEntityType());
            // If joining with e.g. White players only, we still need to filter on this later on as the index will return
            // games where the player is either White or Black
            boolean coveredJoin = entityJoin.joinCondition() == null || entityJoin.joinCondition() == GameEntityJoinCondition.ANY;
            sources.add(GameSourceQuery.fromGameQueryOperator(gameOp.sortedAndDistinct(), true, List.of(), coveredJoin ? List.of(entityJoin) : List.of()));
        }

        for (GameFilter gameFilter : gameQuery.gameFilters()) {
            if (gameFilter instanceof GameEntityFilter<?>) {
                // TODO: A GameEntityFilter should also have a join condition!?
                GameEntityFilter<?> entityFilter = (GameEntityFilter<?>) gameFilter;
                QueryOperator<Game> gameOp = new GameIdsByEntities<>(context, new Manual<>(context, Set.copyOf(entityFilter.entityIds())), entityFilter.entityType());

                sources.add(GameSourceQuery.fromGameQueryOperator(gameOp.sortedAndDistinct(QuerySortOrder.byId(), 0), true, List.of(gameFilter), List.of()));
            }
        }

        return sources;
    }

    <T extends SourceQuery<?>> List<List<T>> sourceCombinations(@NotNull List<T> sources) {
        ArrayList<List<T>> combinations = new ArrayList<>();
        int n = sources.size();
        for (int i = 0; i < (1 << n); i++) {
            HashSet<Object> seenFiltersCovered = new HashSet<>();
            boolean valid = true;
            ArrayList<T> current = new ArrayList<>();
            for (int j = 0; j < n; j++) {
                if (((1 << j) & i) > 0) {
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

    public <T extends Entity & Comparable<T>> List<QueryOperator<T>> getEntityQueryPlans(QueryContext context, EntityQuery<T> entityQuery, boolean fullData) {
        return this.entityQueryPlanner.getQueryPlans(context, entityQuery, fullData);
    }

    public void updatePlanners(QueryPlanner queryPlanner) {
        // TODO: This is ugly, needed for mocking. Perhaps change so planners are passed around instead of QueryContext?
        this.entityQueryPlanner.setQueryPlanner(queryPlanner);
    }
}


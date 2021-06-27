package se.yarin.morphy.queries;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.morphy.Database;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.Game;
import se.yarin.morphy.boosters.GameEntityIndex;
import se.yarin.morphy.entities.*;
import se.yarin.morphy.entities.filters.EntityFilter;
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

    EntitySourceQuery<Player> getPlayerQuery(@NotNull QueryContext context, EntityFilter<Player> filter) {
        if (filter instanceof PlayerNameFilter) {
            PlayerNameFilter playerNameFilter = (PlayerNameFilter) filter;
            if (playerNameFilter.isCaseSensitive()) {
                String lastName = playerNameFilter.lastName();
                PlayerIndexRangeScan playerIndexRangeScan = new PlayerIndexRangeScan(context, playerNameFilter, Player.of(lastName, ""), Player.of(lastName + "zzz", ""));
                return new EntitySourceQuery<>(playerIndexRangeScan);
            }
        }
        return new EntitySourceQuery<>(new PlayerTableScan(context, filter));
    }

    EntitySourceQuery<Tournament> getTournamentQuery(QueryContext context, EntityFilter<Tournament> filter) {
        return new EntitySourceQuery<>(new TournamentTableScan(context, filter));
    }

    List<GameSourceQuery> getGameQuerySources(@NotNull QueryContext context, @NotNull GameQuery gameQuery) {
        List<GameSourceQuery> sources = new ArrayList<>();

        sources.add(GameSourceQuery.fromGameQuery(new GameTableScan(context, CombinedGameFilter.combine(gameQuery.gameFilters())), true));

        for (EntityQuery<?> entityQuery : gameQuery.entityQueries()) {
            throw new MorphyNotSupportedException();
        }

        for (EntityFilter<Player> filter : gameQuery.playerFilters()) {
            EntitySourceQuery<Player> entityQuery = getPlayerQuery(context, filter);
            QueryOperator<Game> gameOp = new Distinct<>(context, new Sort<>(context, new GameIdsByEntities<>(context, entityQuery.entityOperator(), EntityType.PLAYER)));
            sources.add(GameSourceQuery.fromGameQuery(gameOp, true));
        }

        // TODO: try combined as well
        for (EntityFilter<Tournament> filter : gameQuery.tournamentFilters()) {
            EntitySourceQuery<Tournament> entityQuery = getTournamentQuery(context, filter);
            QueryOperator<Game> gameOp = new Distinct<>(context, new Sort<>(context,
                    new GameIdsByEntities<>(context, entityQuery.entityOperator(), EntityType.TOURNAMENT)));
            sources.add(GameSourceQuery.fromGameQuery(gameOp, true));
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

    List<List<EntityFilter<?>>> entityFilterPermutations(@NotNull List<EntityFilter<?>> orgEntityFilters) {
        ArrayList<EntityFilter<?>> entityFilters = new ArrayList<>(orgEntityFilters);
        ArrayList<List<EntityFilter<?>>> permutations = new ArrayList<>();

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
                EntityFilter<?> tmp = entityFilters.get(j);
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

    public List<QueryOperator<Game>> getCandidateQueries(@NotNull QueryContext context, @NotNull GameQuery gameQuery) {
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

        for (List<GameSourceQuery> sourceCombination : sourceCombinations(sources)) {
            // Join the sources starting with the one returning least amount of expected rows
            List<GameSourceQuery> sorted = sourceCombination.stream().sorted(Comparator.comparingLong(GameSourceQuery::estimateRows)).collect(Collectors.toList());

            List<EntityFilter<?>> coveredFilters = new ArrayList<>(); // TODO set this
            GameSourceQuery current = sorted.get(0);
            for (int i = 1; i < sorted.size(); i++) {
                current = GameSourceQuery.join(current, sorted.get(i));
            }

            QueryOperator<Game> gameOperator = current.gameOperator();
            if (!gameOperator.hasFullData()) {
                gameOperator = new GameLookup(context, current.gameOperator(), CombinedGameFilter.combine(gameQuery.gameFilters()));
            }
            assert gameOperator.hasFullData();

            for (List<? extends EntityFilter<?>> entityFilterPermutation : entityFilterPermutations(gameQuery.entityFilters())) {
                QueryOperator<Game> currentGameOperator = gameOperator;
                for (EntityFilter<?> filter : entityFilterPermutation) {
                    if (filter.entityType() == EntityType.PLAYER) {
                        currentGameOperator = new GamePlayerFilter(context, currentGameOperator, (EntityFilter<Player>) filter);
                    } else if (filter.entityType() == EntityType.TOURNAMENT) {
                        currentGameOperator = new GameTournamentFilter(context, currentGameOperator, (EntityFilter<Tournament>) filter);
                    } else {
                        throw new MorphyNotSupportedException();
                    }
                }
                candidateQueryPlans.add(currentGameOperator);
            }
        }

        return candidateQueryPlans;

/*
        List<EntityQuery<Player>> playerQueries = new ArrayList<>(gameQuery.playerQueries());
        List<EntityQuery<Tournament>> tournamentQueries = new ArrayList<>(gameQuery.tournamentQueries());

        // Check filters for possible sources
        List<GameIdsByEntityIds> filterSources = new ArrayList<>();
        for (GameFilter gameFilter : gameQuery.gameFilters()) {
            if (gameFilter instanceof PlayerFilter) {
                filterSources.add(new GameIdsByEntityIds(context, new IntManual(context, ((PlayerFilter) gameFilter).playerIds()), EntityType.PLAYER));
            }
        }

        List<GameIdsByEntityIds> querySources = new ArrayList<>();
        for (EntityQuery<Player> playerQuery : playerQueries) {
            List<QueryOperator<Integer>> subQueryOperators = getEntityIdCandidateQueries(context, playerQuery);
            // TODO: If there are multiple candidates for a complex subquery, pick the best one
            QueryOperator<Integer> bestSubQueryOperator = subQueryOperators.get(0);

            querySources.add(new GameIdsByEntityIds(context, bestSubQueryOperator, EntityType.PLAYER));
        }

        ArrayList<QueryOperator<Integer>> gameQueryOperators = new ArrayList<>();
        // Any subset of the filterSources can be included, but all querySources must be included (one per query)
        for (int i = 0; i < (1 << filterSources.size()); i++) {
            ArrayList<List<GameIdsByEntityIds>> sourceQueries = new ArrayList<>();
            for (int j = 0; j < filterSources.size(); j++) {
                if (((1<<j) & i) > 0) {
                    sourceQueries.add(List.of(filterSources.get(j)));
                }
            }
            sourceQueries.addAll(querySources);

            gameQueryOperators.addAll(joinSourceQueries(context, sourceQueries));
        }

        for (QueryOperator<Integer> gameQueryOperator : gameQueryOperators) {
            // Game lookup and then apply game filters
            new GameLookup(context, gameQueryOperator, new CombinedGameFilter(gameQuery.gameFilters()))
        }

        return null;

 */
    }
/*
    private List<? extends QueryOperator<Integer>> joinSourceQueries(
            @NotNull QueryContext context,
            @NotNull List<List<GameIdsByEntities>> sourceQueries) {
        assert sourceQueries.size() > 0;
        if (sourceQueries.size() == 1) {
            return sourceQueries.get(0);
        }
        ArrayList<QueryOperator<Integer>> possibleJoins = new ArrayList<>();
        // Try all subset splits
        for (int i = 0; i < (1 << sourceQueries.size()); i++) {
            ArrayList<List<GameIdsByEntities>> left = new ArrayList<>();
            ArrayList<List<GameIdsByEntities>> right = new ArrayList<>();
            for (int j = 0; j < sourceQueries.size(); j++) {
                if (((1<<i) & j) > 0) {
                    left.add(sourceQueries.get(j));
                } else {
                    right.add(sourceQueries.get(j));
                }
            }

            var leftSources = joinSourceQueries(context, left);
            var rightSources = joinSourceQueries(context, right);
            for (QueryOperator<Integer> leftSource : leftSources) {
                for (QueryOperator<Integer> rightSource : rightSources) {
                    QueryOperator<Integer> hashJoin = new HashJoin(context, leftSource, rightSource);
                    possibleJoins.add(hashJoin);
                }
            }
        }

        return possibleJoins;
    }

    public <T> List<QueryOperator<T>> getCandidateQueries(@NotNull QueryContext context, @NotNull EntityQuery<T> entityQuery) {
        return List.of();
    }

    public <T> List<QueryOperator<Integer>> getEntityIdCandidateQueries(@NotNull QueryContext context, @NotNull EntityQuery<T> entityQuery) {
        return List.of();
    }

 */
}

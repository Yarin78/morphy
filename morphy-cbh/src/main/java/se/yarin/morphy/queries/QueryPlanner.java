package se.yarin.morphy.queries;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.morphy.Database;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.Game;
import se.yarin.morphy.entities.Entity;
import se.yarin.morphy.entities.EntityIndexReadTransaction;
import se.yarin.morphy.entities.Player;
import se.yarin.morphy.entities.Tournament;
import se.yarin.morphy.entities.filters.EntityFilter;
import se.yarin.morphy.games.filters.GameFilter;

import java.util.ArrayList;
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
}

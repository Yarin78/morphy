package se.yarin.morphy.validation;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.morphy.Database;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.Game;
import se.yarin.morphy.entities.Entity;
import se.yarin.morphy.entities.EntityIndex;
import se.yarin.morphy.entities.EntityIndexReadTransaction;
import se.yarin.morphy.exceptions.MorphyEntityIndexException;

import java.util.EnumSet;
import java.util.Map;
import java.util.TreeSet;


public class EntityStatsValidator {
    private static final Logger log = LoggerFactory.getLogger(EntityStatsValidator.class);

    private final Database database;
    private final EntityStats stats;

    public EntityStatsValidator(@NotNull Database database) {
        this.database = database;
        this.stats = new EntityStats();
    }

    public EntityStats getStats() {
        return stats;
    }

    public void calculateEntityStats(Runnable progressCallback) {
        try (var txn = new DatabaseReadTransaction(database)) {
            for (Game game : txn.iterable()) {
                int gameId = game.id();

                if (!game.guidingText()) {
                    updateEntityStats(stats.players, game.whitePlayerId(), gameId);
                    updateEntityStats(stats.players, game.blackPlayerId(), gameId);
                    updateEntityStats(stats.teams, game.whiteTeamId(), gameId);
                    updateEntityStats(stats.teams, game.blackTeamId(), gameId);
                }
                updateEntityStats(stats.tournaments, game.tournamentId(), gameId);
                updateEntityStats(stats.annotators, game.annotatorId(), gameId);
                updateEntityStats(stats.sources, game.sourceId(), gameId);
                updateEntityStats(stats.gameTags, game.gameTagId(), gameId);

                progressCallback.run();
            }
        }
    }

    private void updateEntityStats(Map<Integer, EntityStats.Stats> map, int entityId, int gameId) {
        if (entityId == -1) {
            // For Teams, -1 is a valid reference meaning "no team"
            return;
        }

        EntityStats.Stats stats = map.get(entityId);
        if (stats == null) {
            map.put(entityId, new EntityStats.Stats(1, gameId));
        } else {
            stats.setCount(stats.getCount() + 1);
        }
    }

    public <T extends Entity & Comparable<T>> void processEntities(
            String entityType,
            EntityIndex<T> entities,
            Map<Integer, EntityStats.Stats> expectedStats,
            boolean throwOnError) throws MorphyEntityIndexException {
        processEntities(entityType, entities, expectedStats, () -> {}, EnumSet.allOf(Validator.Checks.class), 100000, throwOnError);
    }

    public <T extends Entity & Comparable<T>> void processEntities(
            String entityType,
            EntityIndex<T> entityIndex,
            Map<Integer, EntityStats.Stats> expectedStats,
            Runnable progressCallback,
            EnumSet<Validator.Checks> checks,
            int maxInvalid,
            boolean throwOnError) throws MorphyEntityIndexException {
        int numEntities = 0, numEqual = 0;

        TreeSet<Integer> existingIds = new TreeSet<>();

        int numInvalid = 0;
        T last = null;

        EntityIndexReadTransaction<T> txn = entityIndex.beginReadTransaction();
        try {
            for (T current : txn.iterableAscending()) {
                if (numInvalid >= maxInvalid) break;
                existingIds.add(current.id());

                entityIndex.get(current.id()); // Sanity check that we can do this lookup as well

                boolean isValidEntity = true;
                if (checks.contains(Validator.Checks.ENTITY_STATISTICS)) {
                    EntityStats.Stats stats = expectedStats.get(current.id());
                    if (stats == null) {
                        String msg = String.format("Entity in %s base with id %d (%s) occurs in 0 games but stats says %d games and first game %d",
                                entityType, current.id(), current, current.count(), current.firstGameId());
                        if (throwOnError) {
                            throw new MorphyEntityIndexException(msg);
                        }
                        log.warn(msg);
                        isValidEntity = false;
                    } else {
                        if (stats.getCount() != current.count()) {
                            String msg = String.format("Entity in %s base with id %d (%s) has %d games but entity count says %d",
                                    entityType, current.id(), current, stats.getCount(), current.count());
                            if (throwOnError) {
                                throw new MorphyEntityIndexException(msg);
                            }
                            log.warn(msg);
                            isValidEntity = false;
                        }

                        if (stats.getFirstGameId() != current.firstGameId()) {
                            String msg = String.format("Entity in %s base with id %d (%s) first game is %d but stats says %d",
                                    entityType, current.id(), current.toString(), stats.getFirstGameId(), current.firstGameId());
                            if (throwOnError) {
                                throw new MorphyEntityIndexException(msg);
                            }
                            log.warn(msg);
                            isValidEntity = false;
                        }
                    }
                }

                if (current.count() == 0) {
                    // This is a critical error in the ChessBase integrity checker
                    String msg = String.format("Entity in %s base with %d (%s) has 0 count", entityType, current.id(), current.toString());
                    if (throwOnError) {
                        throw new MorphyEntityIndexException(msg);
                    }
                    log.warn(msg);
                    isValidEntity = false;
                }

                if (checks.contains(Validator.Checks.ENTITY_SORT_ORDER)) {
                    if (numEntities > 0) {
                        if (current.compareTo(last) < 0) {
                            String msg = "Wrong order in %s: was (%d) '%s' < (%d) '%s' but expected opposite".formatted(
                                    entityType, last.id(), last, current.id(), current);
                            if (throwOnError) {
                                throw new MorphyEntityIndexException(msg);
                            }
                            log.warn(msg);
                            isValidEntity = false;
                        }
                        if (current.compareTo(last) == 0) {
                            numEqual += 1;
                        }
                    }
                }
                last = current;
                numEntities += 1;
                if (!isValidEntity) {
                    numInvalid++;
                }

                progressCallback.run();
            }
        } finally {
            txn.close();
        }

        if (existingIds.size() != numEntities) {
            log.warn(String.format("Found %d unique %s during sorted iteration, expected to find %d %s",
                    existingIds.size(), entityType, numEntities, entityType));
        }
        if (checks.contains(Validator.Checks.ENTITY_STATISTICS)) {
            if (existingIds.size() != expectedStats.size()) {
                log.warn(String.format("Found %d unique %s during sorted iteration, but has statistics for %d %s",
                        existingIds.size(), entityType, expectedStats.size(), entityType));
            }
            if (expectedStats.containsKey(-1)) {
                log.warn("Invalid reference to id -1 was found");
            }
        }
        if (numEntities != entityIndex.count()) {
            // It's quite often, at least in older databases, off by one; in particular
            // when there are just a few entities in the db
            log.debug(String.format("Iterated over %d %s in ascending order, but header said there were %d %s",
                    numEntities, entityType, entityIndex.count(), entityType));
        }
        if (checks.contains(Validator.Checks.ENTITY_SORT_ORDER)) {
            if (numEqual > 0) {
                log.info(String.format("%d %s with the same key as the previous entity", numEqual, entityType));
            }
        }
        if (entityIndex.capacity() > numEntities) {
            log.debug(String.format("Database has additional capacity for %d %s",
                    entityIndex.capacity() - numEntities, entityType));
        }

        for (Integer id : entityIndex.getDeletedEntityIds()) {
            if (existingIds.contains(id)) {
                log.warn(String.format("Entity id %d was found both when iterating and among deleted nodes", id));
            }
            existingIds.add(id);
        }

        if (existingIds.size() > 0) {
            if (existingIds.first() != 0) {
                log.warn(String.format("First id in %s was %d, expected 0", entityType, existingIds.first()));
            }
            if (existingIds.last() != entityIndex.capacity() - 1) {
                log.warn(String.format("Last id in %s was %d, expected %d",
                        entityType, existingIds.last(), entityIndex.count() - 1));
            }
            if (existingIds.size() != entityIndex.capacity()) {
                log.warn(String.format("Discovered %d entities in %s, but capacity is %d",
                        existingIds.size(), entityType, entityIndex.capacity()));
            }
        }
    }

    public void validateEntityStatistics(boolean throwOnError) throws MorphyEntityIndexException {
        // Used in unit tests
        calculateEntityStats(() -> {});
        processEntities("players", database.playerIndex(), stats.players, throwOnError);
        processEntities("tournaments", database.tournamentIndex(), stats.tournaments, throwOnError);
        processEntities("annotators", database.annotatorIndex(), stats.annotators, throwOnError);
        processEntities("sources", database.sourceIndex(), stats.sources, throwOnError);
        processEntities("teams", database.teamIndex(), stats.teams, throwOnError);
        processEntities("game tags", database.gameTagIndex(), stats.gameTags, throwOnError);
    }

}

package se.yarin.morphy.validation;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.morphy.Database;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.Game;
import se.yarin.morphy.boosters.GameEntityIndex;
import se.yarin.morphy.entities.Entity;
import se.yarin.morphy.entities.EntityIndex;
import se.yarin.morphy.entities.EntityIndexReadTransaction;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.exceptions.MorphyEntityIndexException;

import java.util.*;


public class EntityStatsValidator {
    private static final Logger log = LoggerFactory.getLogger(EntityStatsValidator.class);

    private final Database database;
    private final EntityStats stats;

    private boolean loggedDuplicateNotInGameEntityIndex = false;

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

    private void updateEntityStats(Map<Integer, List<Integer>> map, int entityId, int gameId) {
        if (entityId == -1) {
            // For Teams, -1 is a valid reference meaning "no team"
            return;
        }

        List<Integer> stats = map.get(entityId);
        if (stats == null) {
            ArrayList<Integer> list = new ArrayList<>();
            list.add(gameId);
            map.put(entityId, list);
        } else {
            stats.add(gameId);
        }
    }

    public <T extends Entity & Comparable<T>> void processEntities(
            EntityType entityType,
            EntityIndex<T> entities,
            Map<Integer, List<Integer>> expectedGameIds,
            boolean throwOnError) throws MorphyEntityIndexException {
        EnumSet<Validator.Checks> checks = EnumSet.allOf(Validator.Checks.class);
        checks.remove(Validator.Checks.GAME_ENTITY_INDEX); // TODO: restore this
        processEntities(entityType, entities, expectedGameIds, () -> {}, checks, 100000, throwOnError);
    }

    public <T extends Entity & Comparable<T>> void processEntities(
            EntityType entityType,
            EntityIndex<T> entityIndex,
            Map<Integer, List<Integer>> expectedGameIdsMap,
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
                existingIds.add(current.id());

                entityIndex.get(current.id()); // Sanity check that we can do this lookup as well

                boolean isValidEntity = true;
                if (numInvalid < maxInvalid) {
                    // Don't log to many invalid entities of the same type
                    // We still need to iterate through them all so other structural checks that assumes we've iterated
                    // through everything doesn't fail as a side effect

                    if (checks.contains(Validator.Checks.ENTITY_STATISTICS) || checks.contains(Validator.Checks.GAME_ENTITY_INDEX)) {
                        if (!isEntityStatsValid(expectedGameIdsMap.get(current.id()), current, entityType, throwOnError, checks)) {
                            isValidEntity = false;
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
                    existingIds.size(), entityType.namePlural(), numEntities, entityType.namePlural()));
        }
        if (checks.contains(Validator.Checks.ENTITY_STATISTICS)) {
            if (existingIds.size() != expectedGameIdsMap.size()) {
                log.warn(String.format("Found %d unique %s during sorted iteration, but has statistics for %d %s",
                        existingIds.size(), entityType.namePlural(), expectedGameIdsMap.size(), entityType.namePlural()));
            }
            if (expectedGameIdsMap.containsKey(-1)) {
                log.warn("Invalid reference to id -1 was found");
            }
        }
        if (numEntities != entityIndex.count()) {
            // It's quite often, at least in older databases, off by one; in particular
            // when there are just a few entities in the db
            log.debug(String.format("Iterated over %d %s in ascending order, but header said there were %d %s",
                    numEntities, entityType.namePlural(), entityIndex.count(), entityType.namePlural()));
        }
        if (checks.contains(Validator.Checks.ENTITY_SORT_ORDER)) {
            if (numEqual > 0) {
                log.info(String.format("%d %s with the same key as the previous entity", numEqual, entityType));
            }
        }
        if (entityIndex.capacity() > numEntities) {
            log.debug(String.format("Database has additional capacity for %d %s",
                    entityIndex.capacity() - numEntities, entityType.namePlural()));
        }

        for (Integer id : entityIndex.getDeletedEntityIds()) {
            if (existingIds.contains(id)) {
                log.warn(String.format("Entity id %d was found both when iterating and among deleted nodes", id));
            }
            if (checks.contains(Validator.Checks.GAME_ENTITY_INDEX)) {
                GameEntityIndex gameEntityIndex = database.gameEntityIndex(entityType);
                if (gameEntityIndex != null) {
                    List<Integer> gameIds = gameEntityIndex.getGameIds(id, entityType);
                    if (gameIds.size() != 0) {
                        // Not a critical error
                        log.warn(String.format("Deleted %s entity id %d has %d game references in the Game Entity Index",
                                entityType.nameSingular(), id, gameIds.size()));
                    }
                }
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

    private <T extends Entity & Comparable<T>> boolean isEntityStatsValid(
            List<Integer> expectedGameIds, T current, EntityType entityType, boolean throwOnError, EnumSet<Validator.Checks> checks) {
        if (expectedGameIds == null) {
            String msg = String.format("Entity in %s base with id %d (%s) occurs in 0 games but stats says %d games and first game %d",
                    entityType, current.id(), current, current.count(), current.firstGameId());
            if (throwOnError) {
                throw new MorphyEntityIndexException(msg);
            }
            log.warn(msg);
            return false;
        }

        if (checks.contains(Validator.Checks.ENTITY_STATISTICS)) {
            if (expectedGameIds.size() != current.count()) {
                String msg = String.format("Entity in %s base with id %d (%s) has %d games but entity count says %d",
                        entityType, current.id(), current, expectedGameIds.size(), current.count());
                if (throwOnError) {
                    throw new MorphyEntityIndexException(msg);
                }
                log.warn(msg);
                return false;
            }

            if (expectedGameIds.get(0) != current.firstGameId()) {
                String msg = String.format("Entity in %s base with id %d (%s) first game is %d but stats says %d",
                        entityType, current.id(), current, expectedGameIds.get(0), current.firstGameId());
                if (throwOnError) {
                    throw new MorphyEntityIndexException(msg);
                }
                log.warn(msg);
                return false;
            }
        }
        if (checks.contains(Validator.Checks.GAME_ENTITY_INDEX)) {
            GameEntityIndex gameEntityIndex = this.database.gameEntityIndex(entityType);
            if (gameEntityIndex != null) {
                List<Integer> indexGameIds = gameEntityIndex.getGameIds(current.id(), entityType);
                if (expectedGameIds.size() != indexGameIds.size() || !expectedGameIds.equals(indexGameIds)) {
                    if (new HashSet<>(expectedGameIds).equals(new HashSet<>(indexGameIds))) {
                        // If an entity occurs twice in a game, the index may sometimes only mention it once
                        // (happens in Mega Database 2021). This is less critical.
                        String msg = String.format("%s entity with id %d (%s) was duplicated in a game which was not reflected in the GameEntityIndex (no further warnings of this type will be logged)",
                                entityType.nameSingularCapitalized(), current.id(), current);
                        if (!loggedDuplicateNotInGameEntityIndex) {
                            log.warn(msg);
                            loggedDuplicateNotInGameEntityIndex = true;
                        }
                    } else {
                        String msg = String.format("%s entity with id %d (%s) mismatched in GameEntityIndex",
                                entityType.nameSingularCapitalized(), current.id(), current);
                        if (throwOnError) {
                            throw new MorphyEntityIndexException(msg);
                        }
                        log.warn(msg);
                    }
                    return false;
                }
            }
        }
        return true;
    }

    public void validateEntityStatistics(boolean throwOnError) throws MorphyEntityIndexException {
        // Used in unit tests
        calculateEntityStats(() -> {});
        processEntities(EntityType.PLAYER, database.playerIndex(), stats.players, throwOnError);
        processEntities(EntityType.TOURNAMENT, database.tournamentIndex(), stats.tournaments, throwOnError);
        processEntities(EntityType.ANNOTATOR, database.annotatorIndex(), stats.annotators, throwOnError);
        processEntities(EntityType.SOURCE, database.sourceIndex(), stats.sources, throwOnError);
        processEntities(EntityType.TEAM, database.teamIndex(), stats.teams, throwOnError);
        processEntities(EntityType.GAME_TAG, database.gameTagIndex(), stats.gameTags, throwOnError);
    }

}

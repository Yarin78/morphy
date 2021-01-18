package se.yarin.cbhlib.validation;

import lombok.Getter;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.entities.Entity;
import se.yarin.cbhlib.entities.EntityBase;
import se.yarin.cbhlib.games.ExtendedGameHeader;
import se.yarin.cbhlib.games.GameHeader;
import se.yarin.cbhlib.storage.EntityStorageException;

import java.util.EnumSet;
import java.util.Map;
import java.util.TreeSet;

public class EntityStatsValidator {
    private static final Logger log = LoggerFactory.getLogger(EntityStatsValidator.class);

    private final Database db;
    @Getter
    private final EntityStats stats;

    public EntityStatsValidator(@NonNull Database db) {
        this.db = db;
        this.stats = new EntityStats();
    }

    public void calculateEntityStats(Runnable progressCallback) {
        for (GameHeader gameHeader : db.getHeaderBase().iterable()) {
            int gameId = gameHeader.getId();

            ExtendedGameHeader extendedGameHeader = db.getExtendedHeaderBase().getExtendedGameHeader(gameId);

            if (!gameHeader.isGuidingText()) {
                updateEntityStats(stats.players, gameHeader.getWhitePlayerId(), gameId);
                updateEntityStats(stats.players, gameHeader.getBlackPlayerId(), gameId);
                updateEntityStats(stats.teams, extendedGameHeader.getWhiteTeamId(), gameId);
                updateEntityStats(stats.teams, extendedGameHeader.getBlackTeamId(), gameId);
            }
            updateEntityStats(stats.tournaments, gameHeader.getTournamentId(), gameId);
            updateEntityStats(stats.annotators, gameHeader.getAnnotatorId(), gameId);
            updateEntityStats(stats.sources, gameHeader.getSourceId(), gameId);

            progressCallback.run();
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
            EntityBase<T> entities,
            Map<Integer, EntityStats.Stats> expectedStats,
            boolean throwOnError) throws EntityStorageException {
        processEntities(entityType, entities, expectedStats, () -> {}, EnumSet.allOf(Validator.Checks.class), 100000, throwOnError);
    }

    public <T extends Entity & Comparable<T>> void processEntities(
            String entityType,
            EntityBase<T> entities,
            Map<Integer, EntityStats.Stats> expectedStats,
            Runnable progressCallback,
            EnumSet<Validator.Checks> checks,
            int maxInvalid,
            boolean throwOnError) throws EntityStorageException {
        int numEntities = 0, numEqual = 0;

        TreeSet<Integer> existingIds = new TreeSet<>();

        int numInvalid = 0;
        T last = null;

        for (T current : entities.iterableOrderedAscending()) {
            if (numInvalid >= maxInvalid) break;
            existingIds.add(current.getId());

            entities.get(current.getId()); // Sanity check that we can do this lookup as well

            boolean isValidEntity = true;
            if (checks.contains(Validator.Checks.ENTITY_STATISTICS)) {
                EntityStats.Stats stats = expectedStats.get(current.getId());
                if (stats == null) {
                    String msg = String.format("Entity %d (%s) occurs in 0 games but stats says %d games and first game %d",
                            current.getId(), current.toString(), current.getCount(), current.getFirstGameId());
                    if (throwOnError) {
                        throw new EntityStorageException(msg);
                    }
                    log.warn(msg);
                    isValidEntity = false;
                } else {
                    if (stats.getCount() != current.getCount()) {
                        String msg = String.format("Entity %d (%s) has %d games but stats says %d",
                                current.getId(), current.toString(), stats.getCount(), current.getCount());
                        if (throwOnError) {
                            throw new EntityStorageException(msg);
                        }
                        log.warn(msg);
                        isValidEntity = false;
                    }

                    if (stats.getFirstGameId() != current.getFirstGameId()) {
                        String msg = String.format("Entity %d (%s) first game is %d but stats says %d",
                                current.getId(), current.toString(), stats.getFirstGameId(), current.getFirstGameId());
                        if (throwOnError) {
                            throw new EntityStorageException(msg);
                        }
                        log.warn(msg);
                        isValidEntity = false;
                    }
                }
            }

            if (current.getCount() == 0) {
                String msg = "Entity %d (%s) has 0 count";
                if (throwOnError) {
                    throw new EntityStorageException(msg);
                }
                log.warn(msg);
                isValidEntity = false;
            }

            if (checks.contains(Validator.Checks.ENTITY_SORT_ORDER)) {
                if (numEntities > 0) {
                    if (current.compareTo(last) < 0) {
                        String msg = "Wrong order in %s: was (%d) '%s' < (%d) '%s' but expected opposite".formatted(
                                entityType, last.getId(), last, current.getId(), current);
                        if (throwOnError) {
                            throw new EntityStorageException(msg);
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
        if (numEntities != entities.getCount()) {
            log.warn(String.format("Iterated over %d %s in ascending order, expected to find %d %s",
                    numEntities, entityType, entities.getCount(), entityType));
        }
        if (checks.contains(Validator.Checks.ENTITY_SORT_ORDER)) {
            if (numEqual > 0) {
                log.info(String.format("%d %s with the same key as the previous entity", numEqual, entityType));
            }
        }
        if (entities.getCapacity() > numEntities) {
            log.info(String.format("Database has additional capacity for %d %s",
                    entities.getCapacity() - numEntities, entityType));
        }
        if (existingIds.size() > 0) {
            if (existingIds.first() != 0) {
                log.warn(String.format("First id in %s was %d, expected 0", entityType, existingIds.first()));
            }
            if (existingIds.last() != entities.getCount() - 1) {
                log.warn(String.format("Last id in %s was %d, expected %d",
                        entityType, existingIds.last(), entities.getCount() - 1));
            }
        }
    }

    public void validateEntityStatistics(boolean throwOnError) throws EntityStorageException {
        // Used in unit tests
        calculateEntityStats(() -> {});
        processEntities("players", db.getPlayerBase(), stats.players, throwOnError);
        processEntities("tournaments", db.getTournamentBase(), stats.tournaments, throwOnError);
        processEntities("annotators", db.getAnnotatorBase(), stats.annotators, throwOnError);
        processEntities("sources", db.getSourceBase(), stats.sources, throwOnError);
        processEntities("teams", db.getTeamBase(), stats.teams, throwOnError);
    }

}

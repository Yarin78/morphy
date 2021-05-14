package se.yarin.morphy.validation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.morphy.Database;
import se.yarin.morphy.boosters.GameEntityIndex;
import se.yarin.morphy.entities.EntityIndex;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.exceptions.MorphyEntityIndexException;
import se.yarin.morphy.exceptions.MorphyException;

import java.util.*;

/**
 * Class responsible for performing a series of validation/integrity checks against a database
 */
public class Validator {
    private static final Logger log = LoggerFactory.getLogger(Validator.class);

    // If more than this number of invalid entities are found, stop checking for more
    private static final int MAX_INVALID_ENTITIES = 20;

    public enum Checks {
        // Checks the integrity of the index tree
        ENTITY_DB_INTEGRITY,

        // Checks that entities are sorted correctly
        ENTITY_SORT_ORDER,
        // Checks that statistics (first game and count) for each entity is correct
        ENTITY_STATISTICS,

        // Check Player, Tournament, Annotator, Source, Team and Game Tag entity types (statistics, sorting)
        ENTITY_PLAYERS,
        ENTITY_TOURNAMENTS,
        ENTITY_ANNOTATORS,
        ENTITY_SOURCES,
        ENTITY_TEAMS,
        ENTITY_GAME_TAGS,

        // Check cit/cib files
        GAME_ENTITY_INDEX,

        // Check Game Headers
        GAMES,

        // Deserializes the game data into a GameModel
        GAMES_LOAD,
    }

    private static class EntityTypeCheck {
        private final EntityType entityType;
        private final EntityIndex<?> index;
        private final Map<Integer, List<Integer>> statMap;

        public EntityTypeCheck(EntityType entityType, EntityIndex<?> index, Map<Integer, List<Integer>> statMap) {
            this.entityType = entityType;
            this.index = index;
            this.statMap = statMap;
        }

        public EntityType getEntityType() {
            return entityType;
        }

        public EntityIndex<?> getIndex() {
            return index;
        }

        public Map<Integer, List<Integer>> getStatMap() {
            return statMap;
        }
    }


    /**
     * Performs a series of validation and integrity checks on a database
     * Problems are logged using the logging framework at ERROR or WARN level.
     * INFO and DEBUG messages are used to log various statistics
     * @param db The database to perform validity checks on
     * @param checks The types of checks to perform
     * @param throwOnError If true, throws an exception if a critical error is found
     * @param throwOnWarning If true, throws an exception if non-critical problems are found
     * @param showProgressBar If true, show a progress bar
     */
    public void validate(Database db, EnumSet<Checks> checks, boolean throwOnError, boolean throwOnWarning, boolean showProgressBar)
            throws MorphyException {
        boolean hasCritialErrors = false;

        // Validates the integrity of the binary search tree (proper binary tree)
        List<EntityTypeCheck> entityTypeCheck = new ArrayList<>();

        // Validate entities (statistics, sort order)
        EntityStatsValidator entityStatsValidator = new EntityStatsValidator(db);
        EntityStats entityStats = entityStatsValidator.getStats();

        if (checks.contains(Checks.GAME_ENTITY_INDEX)) {
            if (db.gameEntityIndex() == null) {
                log.error("Game Entity index is missing or corrupt; no further checks will be done on it.");
                checks.remove(Checks.GAME_ENTITY_INDEX);
                hasCritialErrors = true;
            }
        }

        if (checks.contains(Checks.ENTITY_PLAYERS)) {
            entityTypeCheck.add(new EntityTypeCheck(EntityType.PLAYER, db.playerIndex(), entityStats.players));
        }
        if (checks.contains(Checks.ENTITY_TOURNAMENTS)) {
            entityTypeCheck.add(new EntityTypeCheck(EntityType.TOURNAMENT, db.tournamentIndex(), entityStats.tournaments));
        }
        if (checks.contains(Checks.ENTITY_ANNOTATORS)) {
            entityTypeCheck.add(new EntityTypeCheck(EntityType.ANNOTATOR, db.annotatorIndex(), entityStats.annotators));
        }
        if (checks.contains(Checks.ENTITY_SOURCES)) {
            entityTypeCheck.add(new EntityTypeCheck(EntityType.SOURCE, db.sourceIndex(), entityStats.sources));
        }
        if (checks.contains(Checks.ENTITY_TEAMS)) {
            entityTypeCheck.add(new EntityTypeCheck(EntityType.TEAM, db.teamIndex(), entityStats.teams));
        }
        if (checks.contains(Checks.ENTITY_GAME_TAGS)) {
            entityTypeCheck.add(new EntityTypeCheck(EntityType.GAME_TAG, db.gameTagIndex(), entityStats.gameTags));
        }

        TrackerFactory trackerFactory = showProgressBar
                ? new TrackerFactory.ProgressBarTrackerFactory()
                : new TrackerFactory.DummyTrackerFactory();

        if (entityTypeCheck.size() > 0) {
            if (checks.contains(Checks.ENTITY_DB_INTEGRITY)) {
                try (ProgressTracker progressTracker = trackerFactory.create("File structure", entityTypeCheck.size())) {
                    for (EntityTypeCheck typeCheck : entityTypeCheck) {
                        try {
                            typeCheck.getIndex().validateStructure();
                        } catch (MorphyEntityIndexException e) {
                            log.error("Error validating entity integrity for " + typeCheck.entityType + ": " + e.getMessage());
                            hasCritialErrors = true;
                        }
                        progressTracker.step();
                    }
                }
            }

            if (checks.contains(Checks.ENTITY_STATISTICS) || checks.contains(Checks.GAME_ENTITY_INDEX)) {
                try (ProgressTracker progressTracker = trackerFactory.create("Entity stats", db.gameHeaderIndex().count())) {
                    entityStatsValidator.calculateEntityStats(progressTracker::step);
                }
            }

            for (EntityTypeCheck typeCheck : entityTypeCheck) {
                EntityType entityType = typeCheck.getEntityType();
                try (ProgressTracker progressTracker = trackerFactory.create(entityType.namePluralCapitalized(), typeCheck.getIndex().count())) {
                    try {
                        // The stats validator only checks non-critical things, so don't throwOnError
                        // unless we're actually throwing on warnings! (unit tests typically)
                        entityStatsValidator.processEntities(
                                entityType, typeCheck.getIndex(), typeCheck.getStatMap(),
                                progressTracker::step, checks, MAX_INVALID_ENTITIES, throwOnWarning);
                    } catch (Exception e) {
                        log.error(String.format("Error processing %s entities: %s", entityType.nameSingular(), e.getMessage()));
                        hasCritialErrors = true;
                    }
                }
            }
        }

        if (checks.contains(Checks.GAME_ENTITY_INDEX)) {
            if (validateGameEntityIndexBlocks(db)) {
                hasCritialErrors = true;
            }
        }

        if (checks.contains(Checks.GAMES)) {
            GamesValidator gamesValidator = new GamesValidator(db);
            try (ProgressTracker progressTracker = trackerFactory.create("Games", db.gameHeaderIndex().count())) {
                try {
                    int numErrors = gamesValidator.processGames(checks.contains(Checks.GAMES_LOAD), throwOnWarning, progressTracker::step);
                    if (numErrors > 0) {
                        hasCritialErrors = true;
                    }
                } catch (Exception e) {
                    // This shouldn't really happen
                    log.error("Critical error processing games: " + e.getMessage());
                    hasCritialErrors = true;
                }
            }
        }

        if (hasCritialErrors && throwOnError) {
            throw new MorphyException("There were critical errors");
        }
    }

    private boolean validateGameEntityIndexBlocks(Database database) {
        // Checks that all blocks in the cib file are accounted for; returns true if there is an error
        GameEntityIndex gameEntityIndex = database.gameEntityIndex();
        assert gameEntityIndex != null;

        try {
            Set<Integer> cibBlocks = new HashSet<>(), cib2Blocks = new HashSet<>();

            for (EntityType entityType : EntityType.values()) {
                Set<Integer> usedBlockIds = gameEntityIndex.getUsedBlockIds(entityType, database.entityIndex(entityType).capacity());
                if (entityType != EntityType.GAME_TAG) {
                    int expectedSize = cibBlocks.size() + usedBlockIds.size();
                    cibBlocks.addAll(usedBlockIds);
                    if (cibBlocks.size() != expectedSize) {
                        throw new IllegalStateException("Same block used multiple times in game entity index");
                    }
                } else {
                    cib2Blocks.addAll(usedBlockIds);
                }
            }

            List<Integer> cibDeletedBlocks = gameEntityIndex.getDeletedBlockIds(EntityType.PLAYER);
            int expectedSize = cibBlocks.size() + cibDeletedBlocks.size();
            cibBlocks.addAll(cibDeletedBlocks);
            if (cibBlocks.size() != expectedSize) {
                throw new IllegalStateException("Block marked as deleted also in use in game entity index");
            }

            List<Integer> cib2DeletedBlocks = gameEntityIndex.getDeletedBlockIds(EntityType.GAME_TAG);
            expectedSize = cib2Blocks.size() + cib2DeletedBlocks.size();
            cib2Blocks.addAll(cib2DeletedBlocks);
            if (cib2Blocks.size() != expectedSize) {
                throw new IllegalStateException("Block marked as deleted also in use in game entity index");
            }

            int cibNumBlocks = gameEntityIndex.getNumBlocks(EntityType.PLAYER);
            int maxCibBlockIndex = cibBlocks.size() == 0 ? -1 : Collections.max(cibBlocks);
            if (cibNumBlocks != cibBlocks.size() || maxCibBlockIndex != cibNumBlocks - 1) {
                throw new IllegalStateException("There are blocks unaccounted for in the game entity index");
            }

            int cib2NumBlocks = gameEntityIndex.getNumBlocks(EntityType.GAME_TAG);
            int maxCib2BlockIndex = cib2Blocks.size() == 0 ? -1 : Collections.max(cib2Blocks);
            if (cib2NumBlocks != cib2Blocks.size() || maxCib2BlockIndex != cib2NumBlocks - 1) {
                throw new IllegalStateException("There are blocks unaccounted for in the game entity index");
            }
        } catch (IllegalStateException e) {
            log.error(e.getMessage());
            return true;
        }
        return false;
    }
}

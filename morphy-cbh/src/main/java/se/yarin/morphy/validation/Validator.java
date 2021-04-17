package se.yarin.morphy.validation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.morphy.Database;
import se.yarin.morphy.entities.EntityIndex;
import se.yarin.morphy.exceptions.MorphyEntityIndexException;
import se.yarin.morphy.exceptions.MorphyException;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

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

        // Check Game Headers
        GAMES,

        // Deserializes the game data into a GameModel
        GAMES_LOAD,
    }

    private static class EntityTypeCheck {
        private final String entityType;
        private final EntityIndex<?> index;
        private final Map<Integer, EntityStats.Stats> statMap;

        public EntityTypeCheck(String entityType, EntityIndex<?> index, Map<Integer, EntityStats.Stats> statMap) {
            this.entityType = entityType;
            this.index = index;
            this.statMap = statMap;
        }

        public String getEntityType() {
            return entityType;
        }

        public EntityIndex<?> getIndex() {
            return index;
        }

        public Map<Integer, EntityStats.Stats> getStatMap() {
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

        if (checks.contains(Checks.ENTITY_PLAYERS)) {
            entityTypeCheck.add(new EntityTypeCheck("players", db.playerIndex(), entityStats.players));
        }
        if (checks.contains(Checks.ENTITY_TOURNAMENTS)) {
            entityTypeCheck.add(new EntityTypeCheck("tournaments", db.tournamentIndex(), entityStats.tournaments));
        }
        if (checks.contains(Checks.ENTITY_ANNOTATORS)) {
            entityTypeCheck.add(new EntityTypeCheck("annotators", db.annotatorIndex(), entityStats.annotators));
        }
        if (checks.contains(Checks.ENTITY_SOURCES)) {
            entityTypeCheck.add(new EntityTypeCheck("sources", db.sourceIndex(), entityStats.sources));
        }
        if (checks.contains(Checks.ENTITY_TEAMS)) {
            entityTypeCheck.add(new EntityTypeCheck("teams", db.teamIndex(), entityStats.teams));
        }
        if (checks.contains(Checks.ENTITY_GAME_TAGS)) {
            entityTypeCheck.add(new EntityTypeCheck("game tags", db.gameTagIndex(), entityStats.gameTags));
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

            if (checks.contains(Checks.ENTITY_STATISTICS)) {
                try (ProgressTracker progressTracker = trackerFactory.create("Entity stats", db.gameHeaderIndex().count())) {
                    entityStatsValidator.calculateEntityStats(progressTracker::step);
                }
            }

            for (EntityTypeCheck typeCheck : entityTypeCheck) {
                String typeName = typeCheck.getEntityType();
                String typeNameCapitalized = typeName.substring(0, 1).toUpperCase() + typeName.substring(1);
                try (ProgressTracker progressTracker = trackerFactory.create(typeNameCapitalized, typeCheck.getIndex().count())) {
                    try {
                        // The stats validator only checks non-critical things, so don't throwOnError
                        // unless we're actually throwing on warnings! (unit tests typically)
                        entityStatsValidator.processEntities(
                                typeName, typeCheck.getIndex(), typeCheck.getStatMap(),
                                progressTracker::step, checks, MAX_INVALID_ENTITIES, throwOnWarning);
                    } catch (Exception e) {
                        log.error("Error processing entities for " + typeCheck.entityType + ": " + e.getMessage());
                        hasCritialErrors = true;
                    }
                }
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
}

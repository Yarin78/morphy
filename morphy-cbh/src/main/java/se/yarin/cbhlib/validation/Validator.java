package se.yarin.cbhlib.validation;

import lombok.AllArgsConstructor;
import lombok.Data;
import me.tongfei.progressbar.ProgressBar;
import se.yarin.cbhlib.*;
import se.yarin.cbhlib.entities.EntityBase;
import se.yarin.cbhlib.storage.EntityStorageException;

import java.io.IOException;
import java.util.*;

/**
 * Class responsible for performing a series of validation/integrity checks against a database
 */
public class Validator {

    // If more than this number of invalid entities are found, stop checking for more
    private static final int MAX_INVALID_ENTITIES = 20;

    public enum Checks {
        // Checks the integrity of the index tree
        ENTITY_DB_INTEGRITY,

        // Checks that entities are sorted correctly
        ENTITY_SORT_ORDER,
        // Checks that statistics (first game and count) for each entity is correct
        ENTITY_STATISTICS,

        // Check Player, Tournament, Annotator, Source and Team entity types (statistics, sorting)
        ENTITY_PLAYERS,
        ENTITY_TOURNAMENTS,
        ENTITY_ANNOTATORS,
        ENTITY_SOURCES,
        ENTITY_TEAMS,

        // Check Game Headers
        GAMES,

        // Deserializes the game data into a GameModel
        GAMES_LOAD,
    }

    @Data
    @AllArgsConstructor
    private static class EntityTypeCheck {
        private String entityType;
        private EntityBase<?> base;
        private Map<Integer, EntityStats.Stats> statMap;
    }


    /**
     * Performs a series of validation and integrity checks on a database
     * Problems are logged using the logging framework at ERROR or WARN level.
     * INFO and DEBUG messages are used to log various statistics
     * @param db The database to perform validity checks on
     * @param checks The types of checks to perform
     * @param throwOnError If true, throws an exception if a critical error is found
     */
    public void validate(Database db, EnumSet<Checks> checks, boolean throwOnError)
            throws IOException, EntityStorageException {
        // Validates the integrity of the binary search tree (proper binary tree)
        List<EntityTypeCheck> entityTypeCheck = new ArrayList<>();

        // Validate entities (statistics, sort order)
        EntityStatsValidator entityStatsValidator = new EntityStatsValidator(db);
        EntityStats entityStats = entityStatsValidator.getStats();

        if (checks.contains(Checks.ENTITY_PLAYERS)) {
            entityTypeCheck.add(new EntityTypeCheck("players", db.getPlayerBase(), entityStats.players));
        }
        if (checks.contains(Checks.ENTITY_TOURNAMENTS)) {
            entityTypeCheck.add(new EntityTypeCheck("tournaments", db.getTournamentBase(), entityStats.tournaments));
        }
        if (checks.contains(Checks.ENTITY_ANNOTATORS)) {
            entityTypeCheck.add(new EntityTypeCheck("annotators", db.getAnnotatorBase(), entityStats.annotators));
        }
        if (checks.contains(Checks.ENTITY_SOURCES)) {
            entityTypeCheck.add(new EntityTypeCheck("sources", db.getSourceBase(), entityStats.sources));
        }
        if (checks.contains(Checks.ENTITY_TEAMS)) {
            entityTypeCheck.add(new EntityTypeCheck("teams", db.getTeamBase(), entityStats.teams));
        }

        if (entityTypeCheck.size() > 0) {
            if (checks.contains(Checks.ENTITY_DB_INTEGRITY)) {
                try (ProgressBar progressBar = new ProgressBar("File structure", entityTypeCheck.size())) {
                    for (EntityTypeCheck typeCheck : entityTypeCheck) {
                        typeCheck.getBase().getStorage().validateStructure();
                        progressBar.step();
                    }
                }
            }

            if (checks.contains(Checks.ENTITY_STATISTICS)) {
                try (ProgressBar progressBar = new ProgressBar("Entity stats", db.getHeaderBase().size())) {
                    entityStatsValidator.calculateEntityStats(progressBar::step);
                }
            }

            for (EntityTypeCheck typeCheck : entityTypeCheck) {
                String typeName = typeCheck.getEntityType();
                String typeNameCapitalized = typeName.substring(0, 1).toUpperCase() + typeName.substring(1);
                try (ProgressBar progressBar = new ProgressBar(typeNameCapitalized, typeCheck.getBase().getCount())) {
                    entityStatsValidator.processEntities(
                            typeName, typeCheck.getBase(), typeCheck.getStatMap(),
                            progressBar::step, checks, MAX_INVALID_ENTITIES, throwOnError);
                }
            }
        }

        if (checks.contains(Checks.GAMES)) {
            GamesValidator gamesValidator = new GamesValidator(db);
            try (ProgressBar progressBar = new ProgressBar("Games", db.getHeaderBase().size())) {
                gamesValidator.processGames(checks.contains(Checks.GAMES_LOAD), progressBar::step);
            }
        }
    }
}

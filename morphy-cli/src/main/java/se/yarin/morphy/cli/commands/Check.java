package se.yarin.morphy.cli.commands;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import picocli.CommandLine;
import se.yarin.morphy.Database;
import se.yarin.morphy.DatabaseMode;
import se.yarin.morphy.exceptions.MorphyException;
import se.yarin.morphy.validation.Validator;

import java.io.IOException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.concurrent.Callable;

@CommandLine.Command(name = "check", mixinStandardHelpOptions = true)
public class Check extends BaseCommand implements Callable<Integer> {

    private static final Logger log = LogManager.getLogger();

    @CommandLine.Option(names = "--no-progress-bar", negatable = true, description = "Show progress bar")
    private boolean showProgressBar = true;

    @CommandLine.Option(names = "--no-players", negatable = true, description = "Check Player entities (true by default)")
    boolean checkPlayers = true;

    @CommandLine.Option(names = "--no-tournaments", negatable = true, description = "Check Tournament entities (true by default)")
    boolean checkTournaments = true;

    @CommandLine.Option(names = "--no-annotators", negatable = true, description = "Check Annotator entities (true by default)")
    boolean checkAnnotators = true;

    @CommandLine.Option(names = "--no-sources", negatable = true, description = "Check Source entities (true by default)")
    boolean checkSources = true;

    @CommandLine.Option(names = "--no-teams", negatable = true, description = "Check Team entities (true by default)")
    boolean checkTeams = true;

    @CommandLine.Option(names = "--no-game-tags", negatable = true, description = "Check Game Tag entities (true by default)")
    boolean checkGameTags = true;

    @CommandLine.Option(names = "--no-entities", negatable = true, description = "Check entities (true by default)")
    boolean checkEntities = true;

    @CommandLine.Option(names = "--no-entity-stats", negatable = true, description = "Check entity statistics (true by default)")
    boolean checkEntityStats = true;

    @CommandLine.Option(names = "--no-entity-sort-order", negatable = true, description = "Check entity sort order (true by default)")
    boolean checkEntitySortOrder = true;

    @CommandLine.Option(names = "--no-entity-integrity", negatable = true, description = "Check entity file integrity (true by default)")
    boolean checkEntityFileIntegrity = true;

    @CommandLine.Option(names = "--no-game-entity-index", negatable = true, description = "Check game entity index (.cit/.cib) consistency (true by default)")
    boolean checkGameEntityIndex = true;

    @CommandLine.Option(names = "--no-games", negatable = true, description = "Check game headers (true by default)")
    boolean checkGameHeaders = true;

    @CommandLine.Option(names = "--no-load-games", negatable = true, description = "Check all moves, annotations etc in game data (true by default)")
    boolean loadGames = true;

    @Override
    public Integer call() throws IOException {
        setupGlobalOptions();

        HashMap<Validator.Checks, Boolean> checkFlags = new HashMap<>();
        checkFlags.put(Validator.Checks.ENTITY_PLAYERS, checkPlayers && checkEntities);
        checkFlags.put(Validator.Checks.ENTITY_TOURNAMENTS, checkTournaments && checkEntities);
        checkFlags.put(Validator.Checks.ENTITY_ANNOTATORS, checkAnnotators && checkEntities);
        checkFlags.put(Validator.Checks.ENTITY_SOURCES, checkSources && checkEntities);
        checkFlags.put(Validator.Checks.ENTITY_TEAMS, checkTeams && checkEntities);
        checkFlags.put(Validator.Checks.ENTITY_GAME_TAGS, checkGameTags && checkEntities);
        checkFlags.put(Validator.Checks.ENTITY_STATISTICS, checkEntityStats && checkEntities);
        checkFlags.put(Validator.Checks.ENTITY_SORT_ORDER, checkEntitySortOrder && checkEntities);
        checkFlags.put(Validator.Checks.ENTITY_DB_INTEGRITY, checkEntityFileIntegrity && checkEntities);
        checkFlags.put(Validator.Checks.GAME_ENTITY_INDEX, checkGameEntityIndex && checkEntities);
        checkFlags.put(Validator.Checks.GAMES, checkGameHeaders);
        checkFlags.put(Validator.Checks.GAMES_LOAD, loadGames);

        EnumSet<Validator.Checks> checks = EnumSet.allOf(Validator.Checks.class);
        checks.removeIf(flag -> !checkFlags.get(flag));

        getDatabaseStream().forEach(file -> {
            log.info("Opening " + file);

            try (Database db = Database.open(file, DatabaseMode.READ_ONLY)) {
                try {
                    Validator validator = new Validator();
                    db.moveRepository().moveSerializer().setLogDetailedErrors(true);
                    validator.validate(db, checks, true, false, showProgressBar);
                    log.info("Database OK: " + file);

                    if (showInstrumentation()) {
                        db.context().instrumentation().show();
                    }
                } catch (MorphyException e) {
                    // At least one error that the ChessBase integrity checker would consider an error found
                    // It could be just a single game that has some bad moves though
                    if (verboseLevel() > 0) {
                        log.error(e.getMessage());
                    }
                    log.error("Database ERROR: " + file);

                } catch (Exception | AssertionError e) {
                    // Something was not caught properly
                    log.error("Database CRITICAL ERROR: " + file, e);
                }
            } catch (MorphyException | IOException e) {
                log.error("Error opening the database: " + e.getMessage());
            }
        });

        return 0;
    }
}

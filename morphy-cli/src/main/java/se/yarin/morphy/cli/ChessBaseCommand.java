package se.yarin.morphy.cli;

import me.tongfei.progressbar.ProgressBar;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.ConfigurationFactory;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import picocli.CommandLine;
import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.entities.*;
import se.yarin.cbhlib.games.search.*;
import se.yarin.cbhlib.storage.EntityStorageException;
import se.yarin.cbhlib.util.CBUtil;
import se.yarin.cbhlib.validation.Validator;
import se.yarin.morphy.cli.columns.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@CommandLine.Command(name = "cb", description = "Performs an operation on a ChessBase file",
        mixinStandardHelpOptions = true,
        subcommands = { Games.class, Players.class, Tournaments.class, Check.class})
class ChessBaseCommand implements Runnable {

    @Override
    public void run() {
        System.out.println("A subcommand must be specified; use --help");
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new ChessBaseCommand()).execute(args);
        System.exit(exitCode);
    }
}

@CommandLine.Command(name = "games", mixinStandardHelpOptions = true)
class Games implements Callable<Integer> {
    private static final Logger log = LogManager.getLogger();

    @CommandLine.Parameters(index = "0", description = "The ChessBase file to load, or a folder to search in multiple databases")
    private File file;

    @CommandLine.Option(names = {"-R", "--recursive"}, description = "Scan the folder recursively for databases")
    private boolean recursive = false;

    @CommandLine.Option(names = "-v", description = "Output info logging; use twice for debug logging")
    private boolean[] verbose;

    @CommandLine.Option(names = "--limit", description = "Max number of games to output")
    private int limit = 0;

    @CommandLine.Option(names = "--id", description = "The id of a game to get")
    private int[] ids;

    @CommandLine.Option(names = "--count-all", description = "Count all hits, even beyond the limit (if specified)")
    private boolean countAll = false;

    @CommandLine.Option(names = "--player", description = "Show only games with this player (any color)")
    private String[] players;

    @CommandLine.Option(names = "--result", description = "Show only games with this result (1-0, 0-1, draw, win, loss etc)")
    private String result;

    @CommandLine.Option(names = "--date", description = "Date range, e.g. '2015-10-' or '1960-1970'")
    private String dateRange;

    @CommandLine.Option(names = "--team", description = "Show only games where one of the players played for this team")
    private String team;

    @CommandLine.Option(names = "--tournament", description = "Show only games in this tournament")
    private String tournament;

    @CommandLine.Option(names = "--tournament-time", description = "Show only games with this type of time control (normal, rapid, blitz, corr)")
    private String tournamentTimeControl;

    @CommandLine.Option(names = "--tournament-type", description = "Show only games in this type of tournament (tourn, swiss, match etc)")
    private String tournamentType;

    @CommandLine.Option(names = "--tournament-place", description = "Show only games from this tournament place")
    private String tournamentPlace;

    @CommandLine.Option(names = "--rating", description = "Rating range required for both players, e.g. 2700- or 2000-2200")
    private String ratingRangeBoth;

    @CommandLine.Option(names = "--rating.any", description = "Rating range required for at least one player, e.g. 2700- or 2000-2200")
    private String ratingRangeAny;

    @CommandLine.Option(names = "--setup-position", description = "Show only games that has a setup position (does not start at move 1)")
    private boolean setupPosition;

    @CommandLine.Option(names = "--start-position", description = "Show only games that starts at the start position (move 1)")
    private boolean startPosition;

    @CommandLine.Option(names = {"-o", "--output"}, description = "Output database (.cbh or .pgn)")
    private String output;

    @CommandLine.Option(names = "--stats", description = "Show statistics about all matching games")
    private boolean stats;

    @CommandLine.Option(names = "--overwrite", description = "If true, overwrite the output database if it already exists.")
    private boolean overwrite;

    @CommandLine.Option(names = "--columns", description = "A comma separated list on which columns to show. Prefix columns with +/- to only adjust the default columns.")
    private String columns;

    @CommandLine.Option(names = "--raw-col-cbh", description = "Show binary CBH data (debug)")
    private String[] rawCbhColumns;

    @CommandLine.Option(names = "--raw-col-cbj", description = "Show binary CBJ data (debug)")
    private String[] rawCbjColumns;

    @CommandLine.Option(names = "--raw-col-cbg", description = "Show binary CBG data (debug)")
    private boolean rawCbgColumns;

    @CommandLine.Option(names = "--raw-col-cba", description = "Show binary CBA data (debug)")
    private boolean rawCbaColumns;

    @CommandLine.Option(names = "--raw-cbh", description = "Raw filter expression in CBH data (debug)")
    private String[] rawCbhFilter;

    @CommandLine.Option(names = "--raw-cbj", description = "Raw filter expression in CBJ data (debug)")
    private String[] rawCbjFilter;

    @Override
    public Integer call() throws IOException {
        if (verbose != null) {
            String level = verbose.length == 1 ? "info" : "debug";
            org.apache.logging.log4j.core.LoggerContext context = ((org.apache.logging.log4j.core.LoggerContext) LogManager.getContext(false));
            ConfigurationSource configurationSource = ConfigurationSource.fromResource("log4j2-" + level + ".xml", null);
            Configuration configuration = ConfigurationFactory.getInstance().getConfiguration(context, configurationSource);
            context.reconfigure(configuration);
        }
        Locale.setDefault(Locale.US);

        Stream<File> cbhStream;
        if (file.isDirectory()) {
            cbhStream = Files.walk(file.toPath(), recursive ? 30 : 1)
                    .filter(path -> path.toString().toLowerCase().endsWith(".cbh"))
                    .map(Path::toFile);
        } else if (file.isFile()) {
            cbhStream = Stream.of(file);
        } else {
            System.err.println("Database does not exist: " + file);
            return 1;
        }

        GameConsumer gameConsumer = createGameConsumer();
        gameConsumer.init();

        cbhStream.forEach(file -> {
            log.info("Opening " + file);
            try (Database db = Database.open(file)) {
                // Speeds up performance quite a lot, and we should be fairly certain that the moves in the CBH databases are valid
                db.getMovesBase().setValidateDecodedMoves(false);

                GameSearcher gameSearcher = null;
                try {
                    gameSearcher = createGameSearcher(db);
                } catch (IllegalArgumentException e) {
                    System.err.println(e.getMessage());
                    System.exit(1);
                }
                assert gameSearcher != null;

                GameSearcher.SearchResult result;

                if (!(gameConsumer instanceof StdoutGamesSummary)) {
                    try (ProgressBar pb = new ProgressBar("Games", gameSearcher.getTotal())) {
                        result = gameSearcher.search(limit, countAll, gameConsumer, pb::stepTo);
                    }
                } else {
                    result = gameSearcher.search(limit, countAll, gameConsumer, null);
                }

                gameConsumer.searchDone(result);
            } catch (IOException e) {
                System.err.println("IO error when processing " + file);
            }/* catch (RuntimeException e) {
                System.err.println("Unexpected error when processing " + file + ": " + e.getMessage());
            }*/
        });

        gameConsumer.finish();
        return 0;
    }

    public GameConsumer createGameConsumer() throws IOException {
        GameConsumer gameConsumer;
        if (output == null) {
            if (!stats) {
                if (columns == null) {
                    columns = StdoutGamesSummary.DEFAULT_COLUMNS;
                }
                List<GameColumn> parsedColumns = StdoutGamesSummary.parseColumns(this.columns);
                if (rawCbhColumns != null) {
                    for (String rawCbhColumn : rawCbhColumns) {
                        String[] parts = rawCbhColumn.split(",");
                        if (parts.length != 2) {
                            throw new IllegalArgumentException("Invalid format of raw CBH column");
                        }
                        parsedColumns.add(new RawHeaderColumn(Integer.parseInt(parts[0]), Integer.parseInt(parts[1])));
                    }
                }
                if (rawCbjColumns != null) {
                    for (String rawCbjColumn : rawCbjColumns) {
                        String[] parts = rawCbjColumn.split(",");
                        if (parts.length != 2) {
                            throw new IllegalArgumentException("Invalid format of raw CBJ column");
                        }
                        parsedColumns.add(new RawExtendedHeaderColumn(Integer.parseInt(parts[0]), Integer.parseInt(parts[1])));
                    }
                }
                if (rawCbgColumns) {
                    parsedColumns.add(new RawMovesColumn());
                }
                if (rawCbaColumns) {
                    parsedColumns.add(new RawAnnotationsColumn());
                }

                gameConsumer = new StdoutGamesSummary(countAll, parsedColumns);
                if (limit == 0) {
                    limit = 50;
                }
            } else {
                gameConsumer = new StatsGameConsumer();
            }
        } else if (output.endsWith(".cbh")) {
            File file = new File(output);
            if (!overwrite && file.exists()) {
                throw new FileAlreadyExistsException(output);
            }
            if (file.exists()) {
                Database.delete(file);
            }
            gameConsumer = new DatabaseBuilder(file);
        } else {
            throw new IllegalArgumentException("Unknown output format: " + output);
        }
        return gameConsumer;
    }

    public GameSearcher createGameSearcher(Database db) {
        GameSearcher gameSearcher = new GameSearcher(db);

        if (ids != null) {
            gameSearcher.addFilter(new GameIdFilter(db, Arrays.stream(ids).boxed().collect(Collectors.toList())));
        }

        if (setupPosition) {
            gameSearcher.addFilter(new SetupPositionFilter(db, true));
        }

        if (startPosition) {
            gameSearcher.addFilter(new SetupPositionFilter(db, false));
        }

        PlayerSearcher primaryPlayerSearcher = null;
        if (players != null) {
            for (String player : players) {
                PlayerSearcher playerSearcher;
                if (!player.contains("|")) {
                    playerSearcher = new SinglePlayerSearcher(db.getPlayerBase(), player, true, false);
                } else {
                    playerSearcher = new MultiPlayerSearcher(db.getPlayerBase(), player);
                }
                gameSearcher.addFilter(new PlayerFilter(db, playerSearcher, PlayerFilter.PlayerColor.ANY));
                if (primaryPlayerSearcher == null) {
                    primaryPlayerSearcher = playerSearcher;
                }
            }
        }

        if (result != null) {
            if (result.equals("win") || result.equals("loss")) {
                if (primaryPlayerSearcher == null) {
                    throw new IllegalArgumentException("A player search is needed when filtering on 'wins' or 'loss' results");
                }
                gameSearcher.addFilter(new PlayerResultsFilter(db, result, primaryPlayerSearcher));
            } else {
                gameSearcher.addFilter(new ResultsFilter(db, result));
            }
        }

        if (dateRange != null) {
            gameSearcher.addFilter(new DateRangeFilter(db, dateRange));
        }

        if (ratingRangeBoth != null) {
            gameSearcher.addFilter(new RatingRangeFilter(db, ratingRangeBoth, RatingRangeFilter.RatingColor.BOTH));
        }

        if (ratingRangeAny != null) {
            gameSearcher.addFilter(new RatingRangeFilter(db, ratingRangeAny, RatingRangeFilter.RatingColor.ANY));
        }

        if (team != null) {
            gameSearcher.addFilter(new TeamFilter(db, team));
        }

        SingleTournamentSearcher tournamentSearcher = null;
        if (tournament != null) {
            tournamentSearcher = new SingleTournamentSearcher(db.getTournamentBase(), tournament, true, false);
            gameSearcher.addFilter(new TournamentFilter(db, tournamentSearcher));
        }

        if (tournamentTimeControl != null) {
            TournamentTimeControlFilter ttFilter = new TournamentTimeControlFilter(db, tournamentTimeControl);
            gameSearcher.addFilter(ttFilter);
            if (tournamentSearcher != null) {
                tournamentSearcher.setTimeControls(ttFilter.getTimeControls());
            }
        }

        if (tournamentType != null) {
            TournamentTypeFilter ttFilter = new TournamentTypeFilter(db, tournamentType);
            gameSearcher.addFilter(ttFilter);
            if (tournamentSearcher != null) {
                tournamentSearcher.setTypes(ttFilter.getTypes());
            }
        }

        if (tournamentPlace != null) {
            TournamentPlaceFilter tpFilter = new TournamentPlaceFilter(db, tournamentPlace);
            gameSearcher.addFilter(tpFilter);
            if (tournamentSearcher != null) {
                tournamentSearcher.setPlaces(tpFilter.getPlaces());
            }
        }

        if (rawCbhFilter != null) {
            for (String filter : rawCbhFilter) {
                gameSearcher.addFilter(new RawHeaderFilter(db, filter));
            }
        }

        if (rawCbjFilter != null) {
            for (String filter : rawCbjFilter) {
                gameSearcher.addFilter(new RawExtendedHeaderFilter(db, filter));
            }
        }
        return gameSearcher;
    }
}


@CommandLine.Command(name = "players", mixinStandardHelpOptions = true)
class Players implements Callable<Integer> {

    @CommandLine.Parameters(index = "0", description = "The ChessBase file to load")
    private File cbhFile;

    @CommandLine.Option(names = "--count", description = "Max number of players to list")
    int maxPlayers = 20;

    @CommandLine.Option(names = "--hex", description = "Show player key in hexadecimal")
    boolean hex = false;

    @Override
    public Integer call() throws IOException {
        try (Database db = Database.open(cbhFile)) {

            PlayerBase players = db.getPlayerBase();
            int count = 0;
            for (PlayerEntity player : players.iterable()) {
                if (count >= maxPlayers) break;
                String line;
                if (hex) {
                    line = String.format("%7d:  %-30s %-30s %6d", player.getId(), CBUtil.toHexString(player.getRaw()).substring(0, 30), player.getFullName(), player.getCount());
                } else {
                    line = String.format("%7d:  %-30s %6d", player.getId(), player.getFullName(), player.getCount());
                }
                System.out.println(line);
            }
            System.out.println();
            System.out.println("Total: " + players.getCount());
        }

        return 0;
    }
}

@CommandLine.Command(name = "tournaments", mixinStandardHelpOptions = true)
class Tournaments implements Callable<Integer> {

    @CommandLine.Parameters(index = "0", description = "The ChessBase file to load")
    private File cbhFile;

    @CommandLine.Option(names = "--count", description = "Max number of tournaments to list")
    int maxTournaments = 20;

    @CommandLine.Option(names = "--hex", description = "Show tournament key in hexadecimal")
    boolean hex = false;

    @Override
    public Integer call() throws IOException {
        try (Database db = Database.open(cbhFile)) {
            TournamentBase tournaments = db.getTournamentBase();
            int count = 0;
            for (TournamentEntity tournament : tournaments.iterable()) {
                if (count >= maxTournaments) break;
                String line;
                if (hex) {
                    line = String.format("%7d:  %-30s %-30s %6d", tournament.getId(), CBUtil.toHexString(tournament.getRaw()).substring(0, 30), tournament.getTitle(), tournament.getCount());
                } else {
                    line = String.format("%7d:  %-30s %6d", tournament.getId(), tournament.getTitle(), tournament.getCount());
                }
                System.out.println(line);
            }
            System.out.println();
            System.out.println("Total: " + tournaments.getCount());
        }
        return 0;
    }
}

@CommandLine.Command(name = "check", mixinStandardHelpOptions = true)
class Check implements Callable<Integer> {

    private static final Logger log = LogManager.getLogger();

    @CommandLine.Parameters(index = "0", description = "The ChessBase file to load, or a folder to search in multiple databases")
    private File file;

    @CommandLine.Option(names = {"-R", "--recursive"}, description = "Scan the folder recursively for databases")
    private boolean recursive = false;

    @CommandLine.Option(names = "-v", description = "Output info logging; use twice for debug logging")
    private boolean[] verbose;

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

    @CommandLine.Option(names = "--no-entities", negatable = true, description = "Check entities (true by default)")
    boolean checkEntities = true;

    @CommandLine.Option(names = "--no-entity-stats", negatable = true, description = "Check entity statistics (true by default)")
    boolean checkEntityStats = true;

    @CommandLine.Option(names = "--no-entity-sort-order", negatable = true, description = "Check entity sort order (true by default)")
    boolean checkEntitySortOrder = true;

    @CommandLine.Option(names = "--no-entity-integrity", negatable = true, description = "Check entity file integrity (true by default)")
    boolean checkEntityFileIntegrity = true;

    @CommandLine.Option(names = "--no-games", negatable = true, description = "Check game headers (true by default)")
    boolean checkGameHeaders = true;

    @CommandLine.Option(names = "--no-load-games", negatable = true, description = "Check all moves, annotations etc in game data (true by default)")
    boolean loadGames = true;

    @Override
    public Integer call() throws IOException {
        if (verbose != null) {
            String level = verbose.length == 1 ? "info" : "debug";
            org.apache.logging.log4j.core.LoggerContext context = ((org.apache.logging.log4j.core.LoggerContext) LogManager.getContext(false));
            ConfigurationSource configurationSource = ConfigurationSource.fromResource("log4j2-" + level + ".xml", null);
            Configuration configuration = ConfigurationFactory.getInstance().getConfiguration(context, configurationSource);
            context.reconfigure(configuration);
        }
        Locale.setDefault(Locale.US);

        Map<Validator.Checks, Boolean> checkFlags = Map.of(
            Validator.Checks.ENTITY_PLAYERS, checkPlayers && checkEntities,
            Validator.Checks.ENTITY_TOURNAMENTS, checkTournaments && checkEntities,
            Validator.Checks.ENTITY_ANNOTATORS, checkAnnotators && checkEntities,
            Validator.Checks.ENTITY_SOURCES, checkSources && checkEntities,
            Validator.Checks.ENTITY_TEAMS, checkTeams && checkEntities,
            Validator.Checks.ENTITY_STATISTICS, checkEntityStats && checkEntities,
            Validator.Checks.ENTITY_SORT_ORDER, checkEntitySortOrder && checkEntities,
            Validator.Checks.ENTITY_DB_INTEGRITY, checkEntityFileIntegrity && checkEntities,
            Validator.Checks.GAMES, checkGameHeaders,
            Validator.Checks.GAMES_LOAD, loadGames
        );

        Stream<File> cbhStream;
        if (file.isDirectory()) {
            cbhStream = Files.walk(file.toPath(), recursive ? 30 : 1)
                    .filter(path -> path.toString().toLowerCase().endsWith(".cbh"))
                    .map(Path::toFile);
        } else if (file.isFile()) {
            cbhStream = Stream.of(file);
        } else {
            System.err.println("Database does not exist: " + file);
            return 1;
        }

        EnumSet<Validator.Checks> checks = EnumSet.allOf(Validator.Checks.class);
        checks.removeIf(flag -> !checkFlags.get(flag));

        cbhStream.forEach(file -> {
            log.info("Opening " + file);

            try (Database db = Database.open(file)) {
                Validator validator = new Validator();
                validator.validate(db, checks, false);
            } catch (IOException e) {
                log.error("IO error when processing " + file);
            } catch (EntityStorageException e) {
                log.error("Entity storage error: " + e);
            }
        });

        return 0;
    }
}

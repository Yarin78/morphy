package se.yarin.morphy.cli;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.ConfigurationFactory;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import picocli.CommandLine;
import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.entities.*;
import se.yarin.cbhlib.exceptions.ChessBaseException;
import se.yarin.cbhlib.games.GameHeader;
import se.yarin.cbhlib.games.search.DateRangeFilter;
import se.yarin.cbhlib.games.search.GameSearcher;
import se.yarin.cbhlib.games.search.PlayerFilter;
import se.yarin.cbhlib.games.search.RatingRangeFilter;
import se.yarin.cbhlib.storage.EntityStorageException;
import se.yarin.cbhlib.util.CBUtil;
import se.yarin.cbhlib.validation.Validator;
import se.yarin.chess.GameResult;

import java.io.File;
import java.io.IOException;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;

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

    @CommandLine.Parameters(index = "0", description = "The ChessBase file to load")
    private File cbhFile;

    @CommandLine.Option(names = "-v", description = "Output info logging; use twice for debug logging")
    private boolean[] verbose;

    @CommandLine.Option(names = "--limit", description = "Max number of games to output")
    private int limit = 50;

    @CommandLine.Option(names = "--total", description = "Show total number of hits")
    private boolean showTotal = false;

    @CommandLine.Option(names = "--player", description = "Show only games with this player (any color)")
    private String[] players;

    @CommandLine.Option(names = "--date", description = "Date range, e.g. '2015-10-' or '1960-1970'")
    private String dateRange;

    @CommandLine.Option(names = "--rating", description = "Rating range required for both players, e.g. 2700- or 2000-2200")
    private String ratingRangeBoth;

    @CommandLine.Option(names = "--rating.any", description = "Rating range required for at least one player, e.g. 2700- or 2000-2200")
    private String ratingRangeAny;

    @Override
    public Integer call() throws IOException, ChessBaseException {
        if (verbose != null) {
            String level = verbose.length == 1 ? "info" : "debug";
            org.apache.logging.log4j.core.LoggerContext context = ((org.apache.logging.log4j.core.LoggerContext) LogManager.getContext(false));
            ConfigurationSource configurationSource = ConfigurationSource.fromResource("log4j2-" + level + ".xml", null);
            Configuration configuration = ConfigurationFactory.getInstance().getConfiguration(context, configurationSource);
            context.reconfigure(configuration);
        }
        Locale.setDefault(Locale.US);

        try (Database db = Database.open(cbhFile)) {
            GameSearcher gameSearcher = new GameSearcher(db);

            if (players != null) {
                for (String player : players) {
                    PlayerSearcher playerSearcher = new PlayerSearcher(db.getPlayerBase(), player, true, false);
                    gameSearcher.addFilter(new PlayerFilter(db, playerSearcher, PlayerFilter.PlayerColor.ANY));
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

            outputHeader();
            //try (ProgressBar pb = new ProgressBar("Games", gameSearcher.getTotal())) {
            GameSearcher.SearchResult result = gameSearcher.search(limit, showTotal, this::outputHit);

            if (showTotal) {
                if (result.getTotalHits() == 0) {
                    System.out.printf("No hits (%.2f s)%n", result.getElapsedTime() / 1000.0);
                } else {
                    System.out.println();
                    if (result.getHits().size() < result.getTotalHits()) {
                        System.out.printf("%d out of %d hits displayed (%.2f s)%n", result.getHits().size(), result.getTotalHits(), result.getElapsedTime() / 1000.0);
                    } else {
                        System.out.printf("%d hits  (%.2f ms)%n", result.getTotalHits(), result.getElapsedTime() / 1000.0);
                    }
                }
            }
        }
        return 0;
    }

    private void outputHeader() {
        output("Game #",
                "White",
                "",
                "Black",
                "",
                "Res",
                "Mov",
                "ECO",
                "Tournament",
                "Date");
        System.out.println("-".repeat(120));
    }

    private void output(String... columns) {
        System.out.printf("%8s  %-20s %4s  %-20s %4s  %3s %3s  %3s  %-30s  %10s%n", (Object[]) columns);
    }

    private void outputHit(GameSearcher.Hit hit) {
        GameHeader header = hit.getGameHeader();

        int gameId = header.getId();

        String whitePlayer, blackPlayer, whiteElo, blackElo, result, numMoves, eco, tournament;
        if (header.isGuidingText()) {
            whitePlayer = "?"; // TODO: Where is the Text title stored?
            blackPlayer = "";
            whiteElo = "";
            blackElo = "";
            result = "Txt";
            numMoves = "";
            eco = "";
            tournament = "";
        } else {
            PlayerEntity white = hit.getWhite();
            PlayerEntity black = hit.getBlack();
            whitePlayer = white.getFullNameShort();
            blackPlayer = black.getFullNameShort();
            whiteElo = header.getWhiteElo() == 0 ? "" : Integer.toString(header.getWhiteElo());
            blackElo = header.getBlackElo() == 0 ? "" : Integer.toString(header.getBlackElo());
            result = header.getResult().toString();
            if (header.getResult() == GameResult.DRAW) {
                result = "½-½";
            } else if (header.getResult() == GameResult.NOT_FINISHED) {
                result = header.getLineEvaluation().toASCIIString();
            }
            numMoves = header.getNoMoves() > 0 ? Integer.toString(header.getNoMoves()) : "";
            eco = header.getEco().toString().substring(0, 3);
            if (eco.equals("???")) {
                eco = "";
            }
            tournament = hit.getTournament().getTitle();
        }

        String playedDate = header.getPlayedDate().toPrettyString();

        output(Integer.toString(gameId),
                limit(whitePlayer, 20),
                limit(whiteElo, 4),
                limit(blackPlayer, 20),
                limit(blackElo, 4),
                limit(result, 3),
                limit(numMoves, 3),
                limit(eco, 3),
                limit(tournament, 30),
                limit(playedDate, 10));
    }

    private String limit(String s, int n) {
        return s.length() > n ? s.substring(0, n) : s;
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

    @CommandLine.Parameters(index = "0", description = "The ChessBase file to load")
    File cbhFile;

    @CommandLine.Option(names = "--no-players", negatable = true, description = "Check Player entities (true by default)")
    boolean checkPlayers = true;

    @CommandLine.Option(names = "--no-tournaments", negatable = true, description = "Check Tournament entities (true by default)")
    boolean checkTournaments = true;

    @CommandLine.Option(names = "--no-annotators", negatable = true, description = "Check Annotator entities (true by default)")
    boolean checkAnnotators = true;

    @CommandLine.Option(names = "--no-sources", negatable = true, description = "Check Source entities (true by default)")
    boolean checkSources = true;

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
    public Integer call() throws IOException, EntityStorageException {
        Map<Validator.Checks, Boolean> checkFlags = Map.of(
            Validator.Checks.ENTITY_PLAYERS, checkPlayers,
            Validator.Checks.ENTITY_TOURNAMENTS, checkTournaments,
            Validator.Checks.ENTITY_ANNOTATORS, checkAnnotators,
            Validator.Checks.ENTITY_SOURCES, checkSources,
            Validator.Checks.ENTITY_STATISTICS, checkEntityStats,
            Validator.Checks.ENTITY_SORT_ORDER, checkEntitySortOrder,
            Validator.Checks.ENTITY_DB_INTEGRITY, checkEntityFileIntegrity,
            Validator.Checks.GAMES, checkGameHeaders,
            Validator.Checks.GAMES_LOAD, loadGames
        );

        EnumSet<Validator.Checks> checks = EnumSet.allOf(Validator.Checks.class);
        checks.removeIf(flag -> !checkFlags.get(flag));

        try (Database db = Database.open(cbhFile)) {
            Validator validator = new Validator();
            validator.validate(db, checks, false);
        }

        return 0;
    }
}

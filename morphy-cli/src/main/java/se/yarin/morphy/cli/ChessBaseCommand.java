package se.yarin.morphy.cli;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import se.yarin.cbhlib.*;
import se.yarin.cbhlib.entities.EntityStorageException;
import se.yarin.cbhlib.util.CBUtil;
import se.yarin.cbhlib.validation.Validator;

import java.io.File;
import java.io.IOException;
import java.util.*;
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

    @Override
    public Integer call() throws IOException {
        try (Database db = Database.open(cbhFile)) {
            System.out.println(db.getHeaderBase().size() + " games");
        }
        return 0;
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
            Iterator<PlayerEntity> iterator = players.getAscendingIterator();
            int count = 0;
            while (iterator.hasNext() && count < maxPlayers) {
                PlayerEntity player = iterator.next();
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
            Iterator<TournamentEntity> iterator = tournaments.getAscendingIterator();
            int count = 0;
            while (iterator.hasNext() && count < maxTournaments) {
                TournamentEntity tournament = iterator.next();
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

    private static final Logger log = LoggerFactory.getLogger(Check.class);

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

package se.yarin.morphy.tools;

import se.yarin.chess.GameModel;
import se.yarin.chess.GameModelComparator;
import se.yarin.chess.pgn.PgnExporter;
import se.yarin.chess.pgn.PgnFormatOptions;
import se.yarin.chess.pgn.PgnParser;
import se.yarin.morphy.Database;
import se.yarin.morphy.DatabaseMode;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.Game;
import se.yarin.morphy.games.annotations.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Throw-away tool to test PGN round-tripping.
 * Opens a CBH database, iterates through all games, converts each to PGN and back,
 * and compares the original with the round-tripped version.
 */
public class TestPgnRoundTrip {

    /**
     * Statistics for the round-trip test.
     */
    static class Stats {
        int totalGames = 0;
        int identicalGames = 0;
        int gamesWithDifferences = 0;
        int failedGames = 0;
    }

    /**
     * Tests a single game for PGN round-trip consistency.
     *
     * @param game the game to test
     * @param exporter the PGN exporter
     * @param parser the PGN parser
     * @param stats the statistics to update
     */
    static void testSingleGame(Game game, PgnExporter exporter, PgnParser parser, Stats stats) {
        try {
            // Get the original GameModel
            GameModel original = game.getModel();

            original.moves().root().traverseDepthFirst(node ->
                AnnotationConverter.trimAnnotations(node.getAnnotations()));

            // Check if all annotations are supported
            ArrayList<GameQuotationAnnotation> quotes = new ArrayList<>();
            original.moves().root().traverseDepthFirst(node ->
                quotes.addAll(node.getAnnotations().getAllByClass(GameQuotationAnnotation.class)));

            /*
            if (quotes.size() > 0) {
                System.out.println("Game #" + game.id());
                for (GameQuotationAnnotation quote : quotes) {
                    System.out.println("unknown = " + quote.unknown() + ",  hasGame = " + quote.hasGame() + ", plyCount = " + quote.getGameModel().moves().countPly(false));
                }
            }
             */

            // Export to PGN
            String pgn = exporter.exportGame(original);

            // Parse back from PGN
            GameModel roundTripped = parser.parseGame(pgn);

            // Compare
            GameModelComparator.ComparisonResult result =
                    GameModelComparator.compare(original, roundTripped);

            if (result.isIdentical()) {
                stats.identicalGames++;
            } else {
                stats.gamesWithDifferences++;

                if (stats.gamesWithDifferences <= 10) {
                    System.out.println("========================================");
                    System.out.println("Game #" + game.id() + " has differences:");
                    System.out.println("White: " + original.header().getWhite());
                    System.out.println("Black: " + original.header().getBlack());
                    System.out.println();
                    System.out.println(result);
                    System.out.println("PGN:");
                    System.out.println(pgn);
                    System.out.println();
                    return;
                }
            }
        } catch (Exception e) {
            stats.failedGames++;
            if (stats.failedGames <= 10) {
                System.err.println("Failed to process game #" + game.id() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
        return;
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 1 || args.length > 2) {
            System.err.println("Usage: TestPgnRoundTrip <database-path> [gameID]");
            System.err.println("Example: TestPgnRoundTrip /path/to/database.cbh");
            System.err.println("         TestPgnRoundTrip /path/to/database.cbh 42");
            System.err.println();
            System.err.println("If gameID is specified and non-zero, only that game will be tested.");
            System.err.println("Otherwise, all games in the database will be tested.");
            System.exit(1);
        }

        String databasePath = args[0];
        int gameIdToTest = 0;

        if (args.length == 2) {
            try {
                gameIdToTest = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid gameID: " + args[1]);
                System.exit(1);
            }
        }

        File dbFile = new File(databasePath);

        if (!dbFile.exists()) {
            System.err.println("Database file not found: " + databasePath);
            System.exit(1);
        }

        System.out.println("Opening database: " + databasePath);
        Database db = Database.open(dbFile, DatabaseMode.READ_ONLY);

        AnnotationConverter roundTripConverter = AnnotationConverter.getRoundTripConverter();
        PgnExporter exporter = new PgnExporter(
                PgnFormatOptions.DEFAULT_WITHOUT_PLYCOUNT,
                roundTripConverter::convertToPgn);
        PgnParser parser = new PgnParser(roundTripConverter::convertToChessBase);

        Stats stats = new Stats();

        if (gameIdToTest != 0) {
            // Test a single game
            System.out.println("Testing game #" + gameIdToTest + "...");
            System.out.println();

            try {
                Game game = db.getGame(gameIdToTest);

                if (game.guidingText()) {
                    System.err.println("Game #" + gameIdToTest + " is a guiding text, not a game");
                    db.close();
                    System.exit(1);
                }

                stats.totalGames = 1;
                testSingleGame(game, exporter, parser, stats);
            } catch (IllegalArgumentException e) {
                System.err.println("Game #" + gameIdToTest + " not found in database");
                db.close();
                System.exit(1);
            }
        } else {
            // Test all games
            System.out.println("Starting round-trip test...");
            System.out.println();

            try (var txn = new DatabaseReadTransaction(db)) {
                for (Game game : txn.iterable()) {
                    if (game.guidingText()) {
                        continue;
                    }
                    stats.totalGames++;

                    if (stats.totalGames % 1000 == 0) {
                        System.out.println("Processed " + stats.totalGames + " games...");
                    }

                    testSingleGame(game, exporter, parser, stats);
                }
            }
        }


        db.close();

        System.out.println();
        System.out.println("========================================");
        System.out.println("SUMMARY");
        System.out.println("========================================");
        System.out.println("Total games processed: " + stats.totalGames);
        System.out.println("Identical after round-trip: " + stats.identicalGames +
                " (" + String.format("%.2f%%", 100.0 * stats.identicalGames / stats.totalGames) + ")");
        System.out.println("Games with differences: " + stats.gamesWithDifferences +
                " (" + String.format("%.2f%%", 100.0 * stats.gamesWithDifferences / stats.totalGames) + ")");
        System.out.println("Failed games: " + stats.failedGames +
                " (" + String.format("%.2f%%", 100.0 * stats.failedGames / stats.totalGames) + ")");
    }
}

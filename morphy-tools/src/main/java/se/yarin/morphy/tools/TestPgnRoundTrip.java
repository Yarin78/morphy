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
import java.util.HashSet;
import java.util.Set;

/**
 * Throw-away tool to test PGN round-tripping.
 * Opens a CBH database, iterates through all games, converts each to PGN and back,
 * and compares the original with the round-tripped version.
 */
public class TestPgnRoundTrip {

    static Set<Class<?>> supportedAnnotationClasses = Set.of(
            ImmutableTextAfterMoveAnnotation.class,
            ImmutableTextBeforeMoveAnnotation.class,
            ImmutableGraphicalSquaresAnnotation.class,
            ImmutableGraphicalArrowsAnnotation.class,
            ImmutableSymbolAnnotation.class);

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: TestPgnRoundTrip <database-path>");
            System.err.println("Example: TestPgnRoundTrip /path/to/database.cbh");
            System.exit(1);
        }

        String databasePath = args[0];
        File dbFile = new File(databasePath);

        if (!dbFile.exists()) {
            System.err.println("Database file not found: " + databasePath);
            System.exit(1);
        }

        System.out.println("Opening database: " + databasePath);
        Database db = Database.open(dbFile, DatabaseMode.READ_ONLY);

        PgnExporter exporter = new PgnExporter(
                PgnFormatOptions.DEFAULT_WITHOUT_PLYCOUNT,
                AnnotationConverter::convertToGenericAnnotations);
        PgnParser parser = new PgnParser(AnnotationConverter::convertToStorageAnnotations);

        int totalGames = 0;
        int identicalGames = 0;
        int gamesWithDifferences = 0;
        int failedGames = 0;
        int skippedGames = 0;

        System.out.println("Starting round-trip test...");
        System.out.println();

        try (var txn = new DatabaseReadTransaction(db)) {
            for (Game game : txn.iterable()) {
                if (game.guidingText()) {
                    continue;
                    }
                totalGames++;

                if (totalGames % 1000 == 0) {
                    System.out.println("Processed " + totalGames + " games...");
                }

                try {
                    // Get the original GameModel
                    GameModel original = game.getModel();

                    original.moves().root().traverseDepthFirst(node -> AnnotationConverter.trimAnnotations(node.getAnnotations()));


                    Set<Class> annos = new HashSet<>();
                    original.moves().root().traverseDepthFirst(node -> node.getAnnotations().forEach(a -> annos.add(a.getClass())));
                    if (!annos.stream().allMatch(a -> supportedAnnotationClasses.contains(a))) {
                        skippedGames++;
                        continue;
                    }


                    // Export to PGN
                    String pgn = exporter.exportGame(original);

                    // Parse back from PGN
                    GameModel roundTripped = parser.parseGame(pgn);

                    // Compare
                    GameModelComparator.ComparisonResult result =
                            GameModelComparator.compare(original, roundTripped);

                    if (result.isIdentical()) {
                        identicalGames++;
                    } else {
                        gamesWithDifferences++;

                        if (gamesWithDifferences <= 10) {
                            System.out.println("========================================");
                            System.out.println("Game #" + game.id() + " has differences:");
                            System.out.println("White: " + original.header().getWhite());
                            System.out.println("Black: " + original.header().getBlack());
                            System.out.println();
                            System.out.println(result);
                            System.out.println("PGN:");
                            System.out.println(pgn);
                            System.out.println();
                            break;
                        }
                    }
                } catch (Exception e) {
                    failedGames++;
                    if (failedGames <= 10) {
                        System.err.println("Failed to process game #" + game.id() + ": " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            }
        }


        db.close();

        System.out.println();
        System.out.println("========================================");
        System.out.println("SUMMARY");
        System.out.println("========================================");
        System.out.println("Total games processed: " + totalGames);
        System.out.println("Skipped games: " + skippedGames);
        System.out.println("Identical after round-trip: " + identicalGames +
                " (" + String.format("%.2f%%", 100.0 * identicalGames / totalGames) + ")");
        System.out.println("Games with differences: " + gamesWithDifferences +
                " (" + String.format("%.2f%%", 100.0 * gamesWithDifferences / totalGames) + ")");
        System.out.println("Failed games: " + failedGames +
                " (" + String.format("%.2f%%", 100.0 * failedGames / totalGames) + ")");
    }
}

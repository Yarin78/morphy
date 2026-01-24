package se.yarin.morphy;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import se.yarin.chess.GameModel;
import se.yarin.chess.GameModelComparator;
import se.yarin.chess.NAG;
import se.yarin.morphy.games.annotations.*;
import se.yarin.chess.pgn.PgnExporter;
import se.yarin.chess.pgn.PgnFormatException;
import se.yarin.chess.pgn.PgnFormatOptions;
import se.yarin.chess.pgn.PgnParser;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Integration tests for PGN round-trips through the database.
 * Tests that storage annotations survive export to PGN and re-import.
 *
 * Flow: Database (storage annotations) → PGN (generic annotations) → Database (storage annotations)
 */
public class PgnDatabaseRoundTripTest {
    private Database testDatabase;
    private PgnExporter exporter;
    private PgnParser parser;

    @Before
    public void setUp() throws IOException, URISyntaxException {
        // Open the pre-generated test database with various annotation types
        File dbFile = ResourceLoader.materializeDatabaseStream(getClass(), "database/test-annotations", "test-annotations");

        // File dbFile = new File(getClass().getResource("/test-annotations.cbh").toURI());
        testDatabase = Database.open(dbFile, DatabaseMode.READ_ONLY);

        // Configure exporter to convert storage → generic annotations
        exporter = new PgnExporter(
            PgnFormatOptions.DEFAULT,
            AnnotationConverter::convertToGenericAnnotations
        );

        // Configure parser to convert generic → storage annotations
        parser = new PgnParser(AnnotationConverter::convertToStorageAnnotations);
    }

    @After
    public void tearDown() throws Exception {
        if (testDatabase != null) {
            testDatabase.close();
        }
    }

    @Test
    public void testNAGAnnotationsRoundTrip() throws PgnFormatException {
        // Game 1: Simple game with NAG annotations
        Game game = testDatabase.getGame(1);
        GameModel original = game.getModel();

        // Verify original has storage annotations (SymbolAnnotation)
        var e4 = original.moves().root().mainNode();
        assertEquals(1, e4.getAnnotations().size());
        assertTrue("Should have SymbolAnnotation", e4.getAnnotations().get(0) instanceof SymbolAnnotation);
        SymbolAnnotation symbol = (SymbolAnnotation) e4.getAnnotations().get(0);
        assertEquals(NAG.GOOD_MOVE, symbol.moveComment());

        // Export to PGN (converts to generic annotations)
        String pgn = exporter.exportGame(original);
        assertTrue("PGN should contain $1", pgn.contains("$1"));

        // Parse back (converts to storage annotations)
        GameModel roundTripped = parser.parseGame(pgn);

        // Verify annotations match
        var rtE4 = roundTripped.moves().root().mainNode();
        assertEquals(1, rtE4.getAnnotations().size());
        assertTrue("Should have SymbolAnnotation after round-trip",
            rtE4.getAnnotations().get(0) instanceof SymbolAnnotation);
        SymbolAnnotation rtSymbol = (SymbolAnnotation) rtE4.getAnnotations().get(0);
        assertEquals(NAG.GOOD_MOVE, rtSymbol.moveComment());
    }

    @Test
    public void testTextCommentsRoundTrip() throws PgnFormatException {
        // Game 2: Game with text comments
        Game game = testDatabase.getGame(2);
        GameModel original = game.getModel();

        // Verify original has TextAfterMoveAnnotation
        var e4 = original.moves().root().mainNode();
        assertEquals(1, e4.getAnnotations().size());
        assertTrue("Should have TextAfterMoveAnnotation",
            e4.getAnnotations().get(0) instanceof TextAfterMoveAnnotation);
        TextAfterMoveAnnotation text = (TextAfterMoveAnnotation) e4.getAnnotations().get(0);
        assertEquals("Best move", text.text());

        // Export to PGN
        String pgn = exporter.exportGame(original);
        assertTrue("PGN should contain comment", pgn.contains("{Best move}"));

        // Parse back
        GameModel roundTripped = parser.parseGame(pgn);

        // Verify text preserved
        var rtE4 = roundTripped.moves().root().mainNode();
        assertEquals(1, rtE4.getAnnotations().size());
        assertTrue("Should have TextAfterMoveAnnotation after round-trip",
            rtE4.getAnnotations().get(0) instanceof TextAfterMoveAnnotation);
        TextAfterMoveAnnotation rtText = (TextAfterMoveAnnotation) rtE4.getAnnotations().get(0);
        assertEquals("Best move", rtText.text());
    }

    @Test
    public void testMixedAnnotationsRoundTrip() throws PgnFormatException {
        // Game 3: Game with mixed annotations (NAG + text)
        Game game = testDatabase.getGame(3);
        GameModel original = game.getModel();

        // Verify original has both SymbolAnnotation and TextAfterMoveAnnotation
        var e4 = original.moves().root().mainNode();
        assertEquals(2, e4.getAnnotations().size());

        // Export and re-import
        String pgn = exporter.exportGame(original);
        GameModel roundTripped = parser.parseGame(pgn);

        // Verify both annotations preserved
        var rtE4 = roundTripped.moves().root().mainNode();
        assertEquals(2, rtE4.getAnnotations().size());

        boolean hasSymbol = false;
        boolean hasText = false;
        for (var annotation : rtE4.getAnnotations()) {
            if (annotation instanceof SymbolAnnotation sym) {
                hasSymbol = true;
                assertEquals(NAG.GOOD_MOVE, sym.moveComment());
            }
            if (annotation instanceof TextAfterMoveAnnotation txt) {
                hasText = true;
                assertEquals("King's pawn", txt.text());
            }
        }
        assertTrue("Should have SymbolAnnotation", hasSymbol);
        assertTrue("Should have TextAfterMoveAnnotation", hasText);
    }

    @Test
    public void testMultipleNAGsConsolidatedRoundTrip() throws PgnFormatException {
        // Game 4: Game with multiple NAGs consolidated into one SymbolAnnotation
        Game game = testDatabase.getGame(4);
        GameModel original = game.getModel();

        // Verify original has consolidated SymbolAnnotation with multiple NAGs
        var e4 = original.moves().root().mainNode();
        assertEquals(1, e4.getAnnotations().size());
        SymbolAnnotation symbol = (SymbolAnnotation) e4.getAnnotations().get(0);
        assertEquals(NAG.GOOD_MOVE, symbol.moveComment());
        assertEquals(NAG.WHITE_SLIGHT_ADVANTAGE, symbol.lineEvaluation());

        // Export and re-import
        String pgn = exporter.exportGame(original);
        assertTrue("PGN should contain $1", pgn.contains("$1"));
        assertTrue("PGN should contain $14", pgn.contains("$14"));

        GameModel roundTripped = parser.parseGame(pgn);

        // Verify NAGs are re-consolidated
        var rtE4 = roundTripped.moves().root().mainNode();
        assertEquals(1, rtE4.getAnnotations().size());
        SymbolAnnotation rtSymbol = (SymbolAnnotation) rtE4.getAnnotations().get(0);
        assertEquals(NAG.GOOD_MOVE, rtSymbol.moveComment());
        assertEquals(NAG.WHITE_SLIGHT_ADVANTAGE, rtSymbol.lineEvaluation());
    }

    @Test
    public void testVariationsWithAnnotationsRoundTrip() throws PgnFormatException {
        // Game 5: Game with variations and annotations
        Game game = testDatabase.getGame(5);
        GameModel original = game.getModel();

        // Verify main line and variation
        var e4 = original.moves().root().mainNode();
        assertEquals(1, e4.getAnnotations().size());
        assertTrue("e4 should have variations", e4.hasVariations());

        // Export and re-import
        String pgn = exporter.exportGame(original);
        GameModel roundTripped = parser.parseGame(pgn);

        // Verify structure preserved
        var rtE4 = roundTripped.moves().root().mainNode();
        assertEquals(1, rtE4.getAnnotations().size());
        assertTrue("Should have variations", rtE4.hasVariations());

        // Verify variation has annotations (c5 is the variation)
        var c5 = rtE4.children().get(1);
        assertEquals(2, c5.getAnnotations().size());
    }

    @Test
    public void testGraphicalAnnotationsRoundTrip() throws PgnFormatException {
        // Game 6: Game with graphical annotations
        Game game = testDatabase.getGame(6);
        GameModel original = game.getModel();

        // Verify original has graphical annotations
        var e4 = original.moves().root().mainNode();
        assertTrue("Should have multiple annotations", e4.getAnnotations().size() >= 2);

        boolean hasSquares = false;
        boolean hasArrows = false;
        for (var annotation : e4.getAnnotations()) {
            if (annotation instanceof GraphicalSquaresAnnotation) {
                hasSquares = true;
            }
            if (annotation instanceof GraphicalArrowsAnnotation) {
                hasArrows = true;
            }
        }
        assertTrue("Should have GraphicalSquaresAnnotation", hasSquares);
        assertTrue("Should have GraphicalArrowsAnnotation", hasArrows);

        // Export to PGN (graphical annotations encoded as [%csl ...] and [%cal ...])
        String pgn = exporter.exportGame(original);
        assertTrue("PGN should contain [%csl", pgn.contains("[%csl"));
        assertTrue("PGN should contain [%cal", pgn.contains("[%cal"));

        // Parse back (should extract graphical annotations from text)
        GameModel roundTripped = parser.parseGame(pgn);

        // Verify graphical annotations preserved
        var rtE4 = roundTripped.moves().root().mainNode();
        boolean rtHasSquares = false;
        boolean rtHasArrows = false;
        for (var annotation : rtE4.getAnnotations()) {
            if (annotation instanceof GraphicalSquaresAnnotation squares) {
                rtHasSquares = true;
                assertEquals("Should have 2 squares", 2, squares.squares().size());
            }
            if (annotation instanceof GraphicalArrowsAnnotation arrows) {
                rtHasArrows = true;
                assertEquals("Should have 2 arrows", 2, arrows.arrows().size());
            }
        }
        assertTrue("Should have GraphicalSquaresAnnotation after round-trip", rtHasSquares);
        assertTrue("Should have GraphicalArrowsAnnotation after round-trip", rtHasArrows);
    }

    @Test
    public void testWorldChampionshipDatabaseRoundTrip() throws IOException {
        // Open the World Championship database
        File dbFile = ResourceLoader.materializeDatabaseStream(
            getClass(), "database/World-ch", "World-ch");
        Database worldChDb = Database.open(dbFile, DatabaseMode.READ_ONLY);

        PgnExporter worldExporter = new PgnExporter(
            PgnFormatOptions.DEFAULT_WITHOUT_PLYCOUNT,
            AnnotationConverter::convertToGenericAnnotations);
        PgnParser worldParser = new PgnParser(AnnotationConverter::convertToStorageAnnotations);

        int totalGames = 0;
        int identicalGames = 0;
        List<Integer> gamesWithDifferences = new ArrayList<>();
        List<Integer> failedGames = new ArrayList<>();

        try (var txn = new DatabaseReadTransaction(worldChDb)) {
            for (Game game : txn.iterable()) {
                if (game.guidingText()) {
                    continue;
                }
                totalGames++;

                try {
                    GameModel original = game.getModel();

                    // Trim annotations before comparison
                    original.moves().root().traverseDepthFirst(node ->
                        AnnotationConverter.trimAnnotations(node.getAnnotations()));

                    // Export to PGN
                    String pgn = worldExporter.exportGame(original);

                    // Parse back from PGN
                    GameModel roundTripped = worldParser.parseGame(pgn);

                    // Compare
                    GameModelComparator.ComparisonResult result =
                        GameModelComparator.compare(original, roundTripped);

                    if (result.isIdentical()) {
                        identicalGames++;
                    } else {
                        gamesWithDifferences.add(game.id());
                    }
                } catch (Exception e) {
                    failedGames.add(game.id());
                }
            }
        }

        worldChDb.close();

        // Print summary
        System.out.println();
        System.out.println("========================================");
        System.out.println("World Championship Database Round-Trip Summary");
        System.out.println("========================================");
        System.out.println("Total games processed: " + totalGames);
        System.out.println("Identical after round-trip: " + identicalGames +
            " (" + String.format("%.2f%%", 100.0 * identicalGames / totalGames) + ")");
        System.out.println("Games with differences: " + gamesWithDifferences.size() +
            " (" + String.format("%.2f%%", 100.0 * gamesWithDifferences.size() / totalGames) + ")");
        System.out.println("Failed games: " + failedGames.size());

        if (!gamesWithDifferences.isEmpty()) {
            System.out.println();
            System.out.println("Game IDs with differences (first 20): " +
                gamesWithDifferences.subList(0, Math.min(20, gamesWithDifferences.size())));
        }

        if (!failedGames.isEmpty()) {
            System.out.println("Failed game IDs: " + failedGames);
        }

        // Assert all games should match
        assertEquals("All games should be identical after round-trip",
            totalGames, identicalGames);
    }
}

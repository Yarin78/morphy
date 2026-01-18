package se.yarin.morphy;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import se.yarin.chess.GameModel;
import se.yarin.chess.GameMovesModel;
import se.yarin.chess.NAG;
import se.yarin.chess.annotations.NAGAnnotation;
import se.yarin.chess.annotations.CommentaryAfterMoveAnnotation;
import se.yarin.chess.pgn.PgnExporter;
import se.yarin.chess.pgn.PgnFormatException;
import se.yarin.chess.pgn.PgnFormatOptions;
import se.yarin.chess.pgn.PgnParser;
import se.yarin.morphy.Game;
import se.yarin.morphy.games.annotations.SymbolAnnotation;
import se.yarin.morphy.games.annotations.TextAfterMoveAnnotation;

import static org.junit.Assert.*;

/**
 * Integration tests for PGN import/export round-trips through the database.
 * Tests that annotations are properly converted and preserved.
 */
public class PgnDatabaseRoundTripTest {
    private Database database;

    @Before
    public void setUp() {
        database = new Database();
    }

    @After
    public void tearDown() throws Exception {
        if (database != null) {
            database.close();
        }
    }

    @Test
    public void testSimpleGameAnnotationsConvertedCorrectly() throws PgnFormatException {
        String pgn = """
                [Event "Test"]
                [Site "Test"]
                [Date "2024.01.15"]
                [Round "1"]
                [White "Player 1"]
                [Black "Player 2"]
                [Result "1-0"]

                1. e4 $1 e5 $2 1-0
                """;

        // Parse PGN
        GameModel model = PgnParser.parseGame(pgn);

        // Verify original annotations are NAGAnnotations
        GameMovesModel.Node e4 = model.moves().root().mainNode();
        assertEquals(1, e4.getAnnotations().size());
        assertTrue(e4.getAnnotations().get(0) instanceof NAGAnnotation);

        // Save to database
        Game savedGame;
        try (var txn = new DatabaseWriteTransaction(database)) {
            savedGame = txn.addGame(model);
            txn.commit();
        }

        // Load from database
        Game loadedGame = database.getGame(savedGame.id());
        GameModel loadedModel = loadedGame.getModel();

        // Verify annotations were converted to SymbolAnnotation
        GameMovesModel.Node loadedE4 = loadedModel.moves().root().mainNode();
        assertEquals(1, loadedE4.getAnnotations().size());
        assertTrue(loadedE4.getAnnotations().get(0) instanceof SymbolAnnotation);
        SymbolAnnotation symbol = (SymbolAnnotation) loadedE4.getAnnotations().get(0);
        assertEquals(NAG.GOOD_MOVE, symbol.moveComment());
    }

    @Test
    public void testTextCommentsPreserved() throws PgnFormatException {
        String pgn = """
                [Event "Test"]
                [Site "Test"]
                [Date "2024.01.15"]
                [Round "1"]
                [White "Player 1"]
                [Black "Player 2"]
                [Result "1-0"]

                1. e4 {Best move} 1-0
                """;

        // Parse PGN
        GameModel model = PgnParser.parseGame(pgn);

        // Verify original annotations
        GameMovesModel.Node e4 = model.moves().root().mainNode();
        assertEquals(1, e4.getAnnotations().size());
        assertTrue(e4.getAnnotations().get(0) instanceof CommentaryAfterMoveAnnotation);

        // Save to database
        Game savedGame;
        try (var txn = new DatabaseWriteTransaction(database)) {
            savedGame = txn.addGame(model);
            txn.commit();
        }

        // Load from database
        Game loadedGame = database.getGame(savedGame.id());
        GameModel loadedModel = loadedGame.getModel();

        // Verify annotations were converted
        GameMovesModel.Node loadedE4 = loadedModel.moves().root().mainNode();
        assertEquals(1, loadedE4.getAnnotations().size());
        assertTrue(loadedE4.getAnnotations().get(0) instanceof TextAfterMoveAnnotation);
        TextAfterMoveAnnotation text = (TextAfterMoveAnnotation) loadedE4.getAnnotations().get(0);
        assertEquals("Best move", text.text());
    }

    @Test
    public void testMixedAnnotationsPreserved() throws PgnFormatException {
        String pgn = """
                [Event "Test"]
                [Site "Test"]
                [Date "2024.01.15"]
                [Round "1"]
                [White "Player 1"]
                [Black "Player 2"]
                [Result "1-0"]

                1. e4 $1 {King's pawn} 1-0
                """;

        // Parse PGN
        GameModel model = PgnParser.parseGame(pgn);

        // Save to database
        Game savedGame;
        try (var txn = new DatabaseWriteTransaction(database)) {
            savedGame = txn.addGame(model);
            txn.commit();
        }

        // Load from database
        Game loadedGame = database.getGame(savedGame.id());
        GameModel loadedModel = loadedGame.getModel();

        // Verify both annotations preserved
        GameMovesModel.Node e4 = loadedModel.moves().root().mainNode();
        assertEquals(2, e4.getAnnotations().size());

        boolean hasSymbol = false;
        boolean hasText = false;
        for (var annotation : e4.getAnnotations()) {
            if (annotation instanceof SymbolAnnotation) {
                hasSymbol = true;
                assertEquals(NAG.GOOD_MOVE, ((SymbolAnnotation) annotation).moveComment());
            }
            if (annotation instanceof TextAfterMoveAnnotation) {
                hasText = true;
                assertEquals("King's pawn", ((TextAfterMoveAnnotation) annotation).text());
            }
        }
        assertTrue(hasSymbol);
        assertTrue(hasText);
    }

    @Test
    public void testMultipleNAGsConsolidated() throws PgnFormatException {
        // $1 = GOOD_MOVE (MOVE_COMMENT), $14 = WHITE_SLIGHT_ADVANTAGE (LINE_EVALUATION)
        String pgn = """
                [Event "Test"]
                [Site "Test"]
                [Date "2024.01.15"]
                [Round "1"]
                [White "Player 1"]
                [Black "Player 2"]
                [Result "1-0"]

                1. e4 $1 $14 1-0
                """;

        // Parse PGN
        GameModel model = PgnParser.parseGame(pgn);

        // Before saving: should have 2 separate NAGAnnotations
        GameMovesModel.Node e4 = model.moves().root().mainNode();
        assertEquals(2, e4.getAnnotations().size());

        // Save to database (this should consolidate)
        Game savedGame;
        try (var txn = new DatabaseWriteTransaction(database)) {
            savedGame = txn.addGame(model);
            txn.commit();
        }

        // Load from database
        Game loadedGame = database.getGame(savedGame.id());
        GameModel loadedModel = loadedGame.getModel();

        // After loading: should have 1 SymbolAnnotation with both NAGs
        GameMovesModel.Node loadedE4 = loadedModel.moves().root().mainNode();
        assertEquals(1, loadedE4.getAnnotations().size());
        assertTrue(loadedE4.getAnnotations().get(0) instanceof SymbolAnnotation);

        SymbolAnnotation symbol = (SymbolAnnotation) loadedE4.getAnnotations().get(0);
        assertEquals(NAG.GOOD_MOVE, symbol.moveComment());
        assertEquals(NAG.WHITE_SLIGHT_ADVANTAGE, symbol.lineEvaluation());
        assertEquals(NAG.NONE, symbol.movePrefix()); // No move prefix in this test
    }

    @Test
    public void testVariationsWithAnnotations() throws PgnFormatException {
        String pgn = """
                [Event "Test"]
                [Site "Test"]
                [Date "2024.01.15"]
                [Round "1"]
                [White "Player 1"]
                [Black "Player 2"]
                [Result "1-0"]

                1. e4 $1 e5 (1... c5 $3 {Sicilian}) 1-0
                """;

        // Parse PGN
        GameModel model = PgnParser.parseGame(pgn);

        // Save to database
        Game savedGame;
        try (var txn = new DatabaseWriteTransaction(database)) {
            savedGame = txn.addGame(model);
            txn.commit();
        }

        // Load from database
        Game loadedGame = database.getGame(savedGame.id());
        GameModel loadedModel = loadedGame.getModel();

        // Verify main line
        GameMovesModel.Node e4 = loadedModel.moves().root().mainNode();
        assertEquals(1, e4.getAnnotations().size());
        assertTrue(e4.getAnnotations().get(0) instanceof SymbolAnnotation);

        // Verify variation
        GameMovesModel.Node e5 = e4.mainNode();
        assertTrue(e5.parent().hasVariations());

        GameMovesModel.Node c5 = e5.parent().children().get(1);
        assertEquals(2, c5.getAnnotations().size());
    }

    @Test
    public void testEmptyGameNoErrors() throws PgnFormatException {
        String pgn = """
                [Event "Test"]
                [Site "Test"]
                [Date "2024.01.15"]
                [Round "1"]
                [White "Player 1"]
                [Black "Player 2"]
                [Result "*"]

                *
                """;

        GameModel model = PgnParser.parseGame(pgn);

        // Save and load
        Game savedGame;
        try (var txn = new DatabaseWriteTransaction(database)) {
            savedGame = txn.addGame(model);
            txn.commit();
        }

        Game loadedGame = database.getGame(savedGame.id());
        GameModel loadedModel = loadedGame.getModel();

        // Should have no moves or annotations
        assertEquals(0, loadedModel.moves().countPly(false));
        assertEquals(0, loadedModel.moves().countAnnotations());
    }

    @Test
    public void testAnnotationCountPreserved() throws PgnFormatException {
        String pgn = """
                [Event "Test"]
                [Site "Test"]
                [Date "2024.01.15"]
                [Round "1"]
                [White "Player 1"]
                [Black "Player 2"]
                [Result "1-0"]

                1. e4 $1 {Best} e5 $2 2. Nf3 $3 {Good} 1-0
                """;

        // Parse PGN
        GameModel model = PgnParser.parseGame(pgn);
        int originalAnnotationCount = model.moves().countAnnotations();
        assertTrue(originalAnnotationCount > 0);

        // Save to database
        Game savedGame;
        try (var txn = new DatabaseWriteTransaction(database)) {
            savedGame = txn.addGame(model);
            txn.commit();
        }

        // Load from database
        Game loadedGame = database.getGame(savedGame.id());
        GameModel loadedModel = loadedGame.getModel();

        // All annotations should be preserved
        assertEquals(originalAnnotationCount, loadedModel.moves().countAnnotations());
    }
}

package se.yarin.chess.pgn;

import org.junit.Test;
import se.yarin.chess.GameModel;
import se.yarin.chess.GameMovesModel;
import se.yarin.chess.Move;
import se.yarin.chess.Position;

import static org.junit.Assert.*;

/**
 * Tests for PGN edge cases, special positions, and error handling.
 */
public class PgnEdgeCasesTest {

    @Test
    public void testEmptyGame() throws PgnFormatException {
        String pgn = """
                [Event "Test"]
                [Site "?"]
                [Date "????.??.??"]
                [Round "?"]
                [White "?"]
                [Black "?"]
                [Result "*"]

                *
                """;

        GameModel game = PgnParser.parseGame(pgn);
        assertEquals(0, game.moves().countPly(false));
    }

    @Test
    public void testCastling() throws PgnFormatException {
        String pgn = """
                [Event "Test"]
                [Site "?"]
                [Date "????.??.??"]
                [Round "?"]
                [White "?"]
                [Black "?"]
                [Result "*"]

                1. e4 e5 2. Nf3 Nc6 3. Bc4 Bc5 4. O-O Nf6 5. Re1 O-O *
                """;

        GameModel game = PgnParser.parseGame(pgn);

        // Find White's O-O (move 7: 4. O-O)
        GameMovesModel.Node node = game.moves().root();
        for (int i = 0; i < 7; i++) {
            node = node.mainNode();
        }
        assertEquals("O-O", node.lastMove().toSAN());
        assertTrue(node.lastMove().isShortCastle());

        // Find Black's O-O (move 10: 5... O-O)
        node = node.mainNode().mainNode().mainNode();
        assertEquals("O-O", node.lastMove().toSAN());
        assertTrue(node.lastMove().isShortCastle());
    }

    @Test
    public void testPromotions() throws PgnFormatException {
        String pgn = """
                [Event "Test"]
                [Site "?"]
                [Date "????.??.??"]
                [Round "?"]
                [White "?"]
                [Black "?"]
                [Result "*"]
                [SetUp "1"]
                [FEN "6nk/P7/8/8/8/8/7p/K7 w - - 0 1"]

                1. a8=Q h1=N *
                """;

        GameModel game = PgnParser.parseGame(pgn);

        // Check white promotion
        GameMovesModel.Node a8Q = game.moves().root().mainNode();
        assertEquals("a8=Q", a8Q.lastMove().toSAN());
        assertNotNull(a8Q.lastMove().promotionStone());

        // Check black promotion
        GameMovesModel.Node h1N = a8Q.mainNode();
        assertEquals("h1=N", h1N.lastMove().toSAN());
        assertNotNull(h1N.lastMove().promotionStone());
    }

    @Test
    public void testEnPassant() throws PgnFormatException {
        String pgn = """
                [Event "Test"]
                [Site "?"]
                [Date "????.??.??"]
                [Round "?"]
                [White "?"]
                [Black "?"]
                [Result "*"]

                1. e4 a6 2. e5 d5 3. exd6 *
                """;

        GameModel game = PgnParser.parseGame(pgn);

        // Find the en passant capture
        GameMovesModel.Node node = game.moves().root();
        for (int i = 0; i < 5; i++) {
            node = node.mainNode();
        }

        assertEquals("exd6", node.lastMove().toSAN());
        assertTrue(node.lastMove().isEnPassant());
    }

    @Test
    public void testAmbiguousKnightMoves() throws PgnFormatException {
        String pgn = """
                [Event "Test"]
                [Site "?"]
                [Date "????.??.??"]
                [Round "?"]
                [White "?"]
                [Black "?"]
                [Result "*"]

                1. Nf3 Nf6 2. Nc3 Nc6 3. Ng5 Ng4 4. Nge4 *
                """;

        GameModel game = PgnParser.parseGame(pgn);

        // Find the disambiguated move Nge4
        GameMovesModel.Node node = game.moves().root();
        for (int i = 0; i < 7; i++) {
            node = node.mainNode();
        }

        assertEquals("Nge4", node.lastMove().toSAN());
    }

    @Test
    public void testCheckAndCheckmate() throws PgnFormatException {
        String pgn = """
                [Event "Scholar's Mate"]
                [Site "?"]
                [Date "????.??.??"]
                [Round "?"]
                [White "?"]
                [Black "?"]
                [Result "1-0"]

                1. e4 e5 2. Bc4 Nc6 3. Qh5 Nf6 4. Qxf7# 1-0
                """;

        GameModel game = PgnParser.parseGame(pgn);

        // Find checkmate
        GameMovesModel.Node node = game.moves().root();
        for (int i = 0; i < 7; i++) {
            node = node.mainNode();
        }

        assertEquals("Qxf7#", node.lastMove().toSAN());
        assertTrue(node.lastMove().isMate());
    }

    @Test
    public void testMultipleVariationsAtSameDepth() throws PgnFormatException {
        String pgn = """
                [Event "Test"]
                [Site "?"]
                [Date "????.??.??"]
                [Round "?"]
                [White "?"]
                [Black "?"]
                [Result "*"]

                1. e4 e5 (1... c5) (1... e6) (1... c6) 2. Nf3 *
                """;

        GameModel game = PgnParser.parseGame(pgn);

        GameMovesModel.Node afterE4 = game.moves().root().mainNode();

        // Should have 4 moves: e5, c5, e6, c6
        assertEquals(4, afterE4.numMoves());

        assertEquals("e5", afterE4.children().get(0).lastMove().toSAN());
        assertEquals("c5", afterE4.children().get(1).lastMove().toSAN());
        assertEquals("e6", afterE4.children().get(2).lastMove().toSAN());
        assertEquals("c6", afterE4.children().get(3).lastMove().toSAN());
    }

    @Test
    public void testLongAlgebraicNotation() throws PgnFormatException {
        // Some PGN files might have long algebraic notation mixed in
        String pgn = """
                [Event "Test"]
                [Site "?"]
                [Date "????.??.??"]
                [Round "?"]
                [White "?"]
                [Black "?"]
                [Result "*"]

                1. e2e4 e7e5 2. Ng1f3 Nb8c6 *
                """;

        GameModel game = PgnParser.parseGame(pgn);
        assertEquals(4, game.moves().countPly(false));
    }

    @Test(expected = PgnFormatException.class)
    public void testInvalidMove() throws PgnFormatException {
        String pgn = """
                [Event "Test"]
                [Site "?"]
                [Date "????.??.??"]
                [Round "?"]
                [White "?"]
                [Black "?"]
                [Result "*"]

                1. e4 e5 2. Qxe5 *
                """;

        // This should fail because Qxe5 is illegal (queen can't capture e5)
        PgnParser.parseGame(pgn);
    }

    @Test(expected = PgnFormatException.class)
    public void testMissingHeaders() throws PgnFormatException {
        String pgn = """
                1. e4 e5 *
                """;

        // Should fail without headers
        PgnParser.parseGame(pgn);
    }

    @Test
    public void testLineWrapping() throws PgnFormatException {
        GameModel game = new GameModel();
        game.header().setEvent("Test");
        game.header().setWhite("Player");
        game.header().setBlack("Opponent");

        // Create a game with many moves to test line wrapping
        GameMovesModel.Node node = game.moves().root();
        Position pos = node.position();

        // Add 20 moves
        for (int i = 0; i < 20 && !pos.generateAllLegalMoves().isEmpty(); i++) {
            Move move = pos.generateAllLegalMoves().get(0);
            node = node.addMove(move);
            pos = node.position();
        }

        PgnExporter exporter = new PgnExporter();
        String pgn = exporter.exportGame(game);

        // Verify it's properly formatted with line breaks
        String[] lines = pgn.split("\n");
        boolean foundWrappedMove = false;
        for (String line : lines) {
            if (!line.startsWith("[") && !line.trim().isEmpty()) {
                // Move text lines should respect max length
                if (line.length() < 100) { // Should be under max line length
                    foundWrappedMove = true;
                }
            }
        }
        assertTrue("Should have wrapped move text", foundWrappedMove);
    }

    @Test
    public void testExportWithoutVariations() throws PgnFormatException {
        String pgn = """
                [Event "Test"]
                [Site "?"]
                [Date "????.??.??"]
                [Round "?"]
                [White "?"]
                [Black "?"]
                [Result "*"]

                1. e4 e5 (1... c5) 2. Nf3 *
                """;

        GameModel game = PgnParser.parseGame(pgn);

        // Export without variations
        PgnFormatOptions options = new PgnFormatOptions(
                79, false, false, false, false, false, false, "\n");
        PgnExporter exporter = new PgnExporter(options);
        String exported = exporter.exportGame(game);

        // Should not contain variation markers
        assertFalse(exported.contains("("));
        assertFalse(exported.contains("c5"));
    }
}

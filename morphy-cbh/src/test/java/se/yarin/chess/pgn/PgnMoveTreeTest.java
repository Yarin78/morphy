package se.yarin.chess.pgn;

import org.junit.Test;
import se.yarin.chess.*;
import se.yarin.chess.annotations.Annotations;
import se.yarin.chess.annotations.CommentaryAfterMoveAnnotation;
import se.yarin.chess.annotations.NAGAnnotation;

import static org.junit.Assert.*;

/**
 * Tests for PGN move tree parsing, including variations and annotations.
 * Verifies the game tree structure node by node.
 */
public class PgnMoveTreeTest {

    private final String SEVEN_TAG_ROSTER = """
            [Event "Test"]
            [Site "?"]
            [Date "????.??.??"]
            [Round "?"]
            [White "?"]
            [Black "?"]
            [Result "*"]

            """;

    @Test
    public void testSimpleMainLine() throws PgnFormatException {
        String pgn = SEVEN_TAG_ROSTER + """                
                1. e4 e5 2. Nf3 Nc6 3. Bb5 *
                """;

        GameModel game = new PgnParser().parseGame(pgn);
        GameMovesModel moves = game.moves();

        // Verify we have a simple main line with 5 plies
        assertEquals(5, moves.countPly(false));

        // Walk the tree and verify each move
        GameMovesModel.Node node = moves.root();

        // Move 1: e4
        assertTrue(node.hasMoves());
        assertFalse(node.hasVariations());
        node = node.mainNode();
        assertEquals("e4", node.lastMove().toSAN());
        assertEquals(1, node.ply());

        // Move 1... e5
        assertTrue(node.hasMoves());
        node = node.mainNode();
        assertEquals("e5", node.lastMove().toSAN());
        assertEquals(2, node.ply());

        // Move 2. Nf3
        assertTrue(node.hasMoves());
        node = node.mainNode();
        assertEquals("Nf3", node.lastMove().toSAN());
        assertEquals(3, node.ply());

        // Move 2... Nc6
        assertTrue(node.hasMoves());
        node = node.mainNode();
        assertEquals("Nc6", node.lastMove().toSAN());
        assertEquals(4, node.ply());

        // Move 3. Bb5
        assertTrue(node.hasMoves());
        node = node.mainNode();
        assertEquals("Bb5", node.lastMove().toSAN());
        assertEquals(5, node.ply());

        // End of game
        assertFalse(node.hasMoves());
    }

    @Test
    public void testSingleVariation() throws PgnFormatException {
        String pgn = SEVEN_TAG_ROSTER + """
                1. e4 e5 (1... c5 2. Nf3) 2. Nf3 Nc6 *
                """;

        GameModel game = new PgnParser().parseGame(pgn);
        GameMovesModel moves = game.moves();

        // Main line has 4 plies, variation adds 2 more
        assertEquals(4, moves.countPly(false));
        assertEquals(6, moves.countPly(true));

        // Navigate to after 1. e4
        GameMovesModel.Node afterE4 = moves.root().mainNode();
        assertEquals("e4", afterE4.lastMove().toSAN());

        // Should have 2 moves: main line (e5) and variation (c5)
        assertEquals(2, afterE4.numMoves());
        assertTrue(afterE4.hasVariations());

        // Main line: 1... e5
        GameMovesModel.Node mainLine = afterE4.children().get(0);
        assertEquals("e5", mainLine.lastMove().toSAN());

        // Variation: 1... c5
        GameMovesModel.Node variation = afterE4.children().get(1);
        assertEquals("c5", variation.lastMove().toSAN());

        // Continue variation: 2. Nf3
        assertTrue(variation.hasMoves());
        GameMovesModel.Node varContinuation = variation.mainNode();
        assertEquals("Nf3", varContinuation.lastMove().toSAN());
    }

    @Test
    public void testNestedVariations() throws PgnFormatException {
        String pgn = SEVEN_TAG_ROSTER + """
                1. e4 e5 (1... c5 2. Nf3 (2. c3) 2... d6) 2. Nf3 *
                """;

        GameModel game = new PgnParser().parseGame(pgn);
        GameMovesModel moves = game.moves();

        // Navigate to variation after 1. e4 e5
        GameMovesModel.Node afterE4 = moves.root().mainNode();
        GameMovesModel.Node c5 = afterE4.children().get(1); // 1... c5
        assertEquals("c5", c5.lastMove().toSAN());

        // After c5, there should be Nf3 (main) and c3 (variation)
        assertTrue(c5.hasVariations());
        assertEquals(2, c5.numMoves());

        GameMovesModel.Node nf3 = c5.children().get(0);
        assertEquals("Nf3", nf3.lastMove().toSAN());

        GameMovesModel.Node c3 = c5.children().get(1);
        assertEquals("c3", c3.lastMove().toSAN());

        // After Nf3, there should be d6
        GameMovesModel.Node d6 = nf3.mainNode();
        assertEquals("d6", d6.lastMove().toSAN());
    }

    @Test
    public void testNAGAnnotations() throws PgnFormatException {
        String pgn = SEVEN_TAG_ROSTER + """
                1. e4 $1 e5 $2 $3 2. Nf3 $4 *
                """;

        GameModel game = new PgnParser().parseGame(pgn);
        GameMovesModel moves = game.moves();

        // Check e4 has NAG $1 (GOOD_MOVE)
        GameMovesModel.Node e4 = moves.root().mainNode();
        NAGAnnotation nag1 = e4.getAnnotation(NAGAnnotation.class);
        assertNotNull(nag1);
        assertEquals(NAG.GOOD_MOVE, nag1.getNag());

        // Check e5 has NAG $2 (BAD_MOVE) and $3 (VERY_GOOD_MOVE)
        GameMovesModel.Node e5 = e4.mainNode();
        Annotations nags = e5.getAnnotations();
        assertTrue(nags.contains(new NAGAnnotation(NAG.BAD_MOVE)));
        assertTrue(nags.contains(new NAGAnnotation(NAG.VERY_GOOD_MOVE)));

        // Check Nf3 has NAG $3 (BLUNDER)
        GameMovesModel.Node nf3 = e5.mainNode();
        NAGAnnotation nag3 = nf3.getAnnotation(NAGAnnotation.class);
        assertNotNull(nag3);
        assertEquals(NAG.BLUNDER, nag3.getNag());
    }

    @Test
    public void testCommentAnnotations() throws PgnFormatException {
        String pgn = SEVEN_TAG_ROSTER + """
                1. e4 {Best by test} e5 {Classical response} *
                """;

        GameModel game = new PgnParser().parseGame(pgn);
        GameMovesModel moves = game.moves();

        // Check e4 has comment
        GameMovesModel.Node e4 = moves.root().mainNode();
        CommentaryAfterMoveAnnotation comment1 = e4.getAnnotation(CommentaryAfterMoveAnnotation.class);
        assertNotNull(comment1);
        assertEquals("Best by test", comment1.getCommentary());

        // Check e5 has comment
        GameMovesModel.Node e5 = e4.mainNode();
        CommentaryAfterMoveAnnotation comment2 = e5.getAnnotation(CommentaryAfterMoveAnnotation.class);
        assertNotNull(comment2);
        assertEquals("Classical response", comment2.getCommentary());
    }

    @Test
    public void testMoveTreeExport() {
        // Create a game with variations
        GameModel game = new GameModel();
        GameMovesModel moves = game.moves();

        // 1. e4
        GameMovesModel.Node e4 = moves.root().addMove(new ShortMove(Chess.E2, Chess.E4));

        // 1... e5 (main line)
        GameMovesModel.Node e5 = e4.addMove(new ShortMove(Chess.E7, Chess.E5));

        // 1... c5 (variation)
        e4.addMove(new ShortMove(Chess.C7, Chess.C5));

        // 2. Nf3 (after e5)
        e5.addMove(new ShortMove(Chess.G1, Chess.F3));

        PgnExporter exporter = new PgnExporter();
        String pgn = exporter.exportGame(game);

        System.out.println(pgn);
        assertTrue(pgn.contains("1. e4 e5 (1... c5) 2. Nf3 *"));
    }

    @Test
    public void testComplexGame() throws PgnFormatException {
        String pgn = SEVEN_TAG_ROSTER + """
                1. e4 e5 (1... c5 $6 {Sicilian} 2. Nf3 d6 (2... Nc6) 3. d4) 2. Nf3 $1 Nc6 3. Bb5 {Ruy Lopez} *
                """;

        GameModel game = new PgnParser().parseGame(pgn);
        GameMovesModel moves = game.moves();

        // Verify main line
        GameMovesModel.Node node = moves.root();
        node = node.mainNode(); // e4
        assertEquals("e4", node.lastMove().toSAN());

        // After e4, should have e5 and c5
        assertTrue(node.hasVariations());
        assertEquals(2, node.numMoves());

        node = node.mainNode(); // e5
        assertEquals("e5", node.lastMove().toSAN());

        node = node.mainNode(); // Nf3
        assertEquals("Nf3", node.lastMove().toSAN());

        // Nf3 should have NAG
        NAGAnnotation nag = node.getAnnotation(NAGAnnotation.class);
        assertNotNull(nag);
        assertEquals(NAG.GOOD_MOVE, nag.getNag());

        node = node.mainNode(); // Nc6
        assertEquals("Nc6", node.lastMove().toSAN());

        node = node.mainNode(); // Bb5
        assertEquals("Bb5", node.lastMove().toSAN());

        // Bb5 should have comment
        CommentaryAfterMoveAnnotation comment = node.getAnnotation(CommentaryAfterMoveAnnotation.class);
        assertNotNull(comment);
        assertEquals("Ruy Lopez", comment.getCommentary());
    }
}

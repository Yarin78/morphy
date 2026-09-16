package se.yarin.chess.pgn;

import org.junit.Test;
import se.yarin.chess.*;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import static org.junit.Assert.*;

/**
 * Round-trip tests for PGN: parse → export → parse verification.
 * Also includes random game generation for stress testing.
 */
public class PgnRoundTripTest {

    @Test
    public void testSimpleGameRoundTrip() throws PgnFormatException {
        String originalPgn = """
                [Event "Test Event"]
                [Site "Test Site"]
                [Date "2024.01.15"]
                [Round "1"]
                [White "Player 1"]
                [Black "Player 2"]
                [Result "1-0"]

                1. e4 e5 2. Nf3 Nc6 3. Bb5 a6 4. Ba4 Nf6 5. O-O Be7 6. Re1 b5 7. Bb3 O-O 1-0
                """;

        // Parse
        GameModel game1 = new PgnParser().parseGame(originalPgn);

        // Export
        PgnExporter exporter = new PgnExporter();
        String exportedPgn = exporter.exportGame(game1);

        // Parse again
        GameModel game2 = new PgnParser().parseGame(exportedPgn);

        // Compare game trees
        assertGamesEqual(game1, game2);
    }

    @Test
    public void testGameWithVariationsRoundTrip() throws PgnFormatException {
        String originalPgn = """
                [Event "Test"]
                [Site "?"]
                [Date "????.??.??"]
                [Round "?"]
                [White "?"]
                [Black "?"]
                [Result "*"]

                1. e4 e5 (1... c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4) 2. Nf3 Nc6 (2... Nf6) 3. Bb5 a6 *
                """;

        GameModel game1 = new PgnParser().parseGame(originalPgn);
        PgnExporter exporter = new PgnExporter();
        String exportedPgn = exporter.exportGame(game1);
        GameModel game2 = new PgnParser().parseGame(exportedPgn);

        assertGamesEqual(game1, game2);
    }

    @Test
    public void testGamePrefaceCommentRoundTrip() throws PgnFormatException {
        // A plain comment before the first move is a whole-game preface, not "about" move 1: it
        // should attach to the root, be exported with no stray leading space, and round-trip.
        String originalPgn = """
                [Event "Test"]
                [Site "?"]
                [Date "????.??.??"]
                [Round "?"]
                [White "?"]
                [Black "?"]
                [Result "*"]

                { A preface comment } 1. e4 e5 2. Nf3 *
                """;

        GameModel game1 = new PgnParser().parseGame(originalPgn);
        assertEquals(
                "A preface comment",
                game1.moves().root().getAnnotations()
                        .getByClass(se.yarin.chess.annotations.CommentaryAfterMoveAnnotation.class)
                        .getCommentary());
        assertNull(
                "The comment must not have landed on move 1",
                game1.moves().root().mainNode().getAnnotations()
                        .getByClass(se.yarin.chess.annotations.CommentaryBeforeMoveAnnotation.class));

        PgnExporter exporter = new PgnExporter();
        String exportedPgn = exporter.exportGame(game1);
        assertTrue(
                "Exported movetext must not have a leading space before the preface comment",
                exportedPgn.contains("\n{ A preface comment } 1. e4"));

        GameModel game2 = new PgnParser().parseGame(exportedPgn);
        assertGamesEqual(game1, game2);
        assertEquals(
                "A preface comment",
                game2.moves().root().getAnnotations()
                        .getByClass(se.yarin.chess.annotations.CommentaryAfterMoveAnnotation.class)
                        .getCommentary());
    }

    @Test
    public void testExplicitBeforeMoveCommentAtGameStartRoundTrip() throws PgnFormatException {
        // An explicitly [%pre]-marked comment is about the next move even at the very start of
        // the game, so it must still attach to move 1, not the root.
        String originalPgn = """
                [Event "Test"]
                [Site "?"]
                [Date "????.??.??"]
                [Round "?"]
                [White "?"]
                [Black "?"]
                [Result "*"]

                { [%pre 1] Before move 1 } 1. e4 e5 2. Nf3 *
                """;

        GameModel game1 = new PgnParser().parseGame(originalPgn);
        assertNull(
                "The marked comment must not have landed on the root",
                game1.moves().root().getAnnotations()
                        .getByClass(se.yarin.chess.annotations.CommentaryAfterMoveAnnotation.class));
        assertNotNull(
                "The marked comment must have landed on move 1 instead",
                game1.moves().root().mainNode().getAnnotations()
                        .getByClass(se.yarin.chess.annotations.CommentaryBeforeMoveAnnotation.class));

        PgnExporter exporter = new PgnExporter();
        String exportedPgn = exporter.exportGame(game1);
        GameModel game2 = new PgnParser().parseGame(exportedPgn);
        assertGamesEqual(game1, game2);
    }

    @Test
    public void testGameWithAnnotationsRoundTrip() throws PgnFormatException {
        String originalPgn = """
                [Event "Test"]
                [Site "?"]
                [Date "????.??.??"]
                [Round "?"]
                [White "?"]
                [Black "?"]
                [Result "*"]

                1. e4 $1 {Best move} e5 $2 {Dubious} 2. Nf3 $3 Nc6 *
                """;

        GameModel game1 = new PgnParser().parseGame(originalPgn);
        PgnExporter exporter = new PgnExporter();
        String exportedPgn = exporter.exportGame(game1);
        GameModel game2 = new PgnParser().parseGame(exportedPgn);

        // Verify move count
        assertEquals(game1.moves().countPly(true), game2.moves().countPly(true));

        // Verify annotations are preserved
        GameMovesModel.Node e4_1 = game1.moves().root().mainNode();
        GameMovesModel.Node e4_2 = game2.moves().root().mainNode();
        assertEquals(e4_1.getAnnotations().size(), e4_2.getAnnotations().size());
    }

    @Test
    public void testRandomGameRoundTrip() throws PgnFormatException {
        Random random = new Random(42); // Fixed seed for reproducibility

        // Generate a random game
        GameModel game1 = generateRandomGame(random, 30);

        // Export
        PgnExporter exporter = new PgnExporter();
        String pgn = exporter.exportGame(game1);

        // Parse
        GameModel game2 = new PgnParser().parseGame(pgn);

        // Verify
        assertGamesEqual(game1, game2);
    }

    @Test
    public void testMultipleRandomGames() throws PgnFormatException {
        Random random = new Random(123);

        for (int i = 0; i < 10; i++) {
            GameModel game1 = generateRandomGame(random, 20 + random.nextInt(40));

            PgnExporter exporter = new PgnExporter();
            String pgn = exporter.exportGame(game1);

            GameModel game2 = new PgnParser().parseGame(pgn);

            assertGamesEqual(game1, game2);
        }
    }

    @Test
    public void testLongRandomGame() throws PgnFormatException {
        Random random = new Random(999);

        // Generate a long game (up to 100 moves)
        GameModel game1 = generateRandomGame(random, 100);

        PgnExporter exporter = new PgnExporter();
        String pgn = exporter.exportGame(game1);

        GameModel game2 = new PgnParser().parseGame(pgn);

        assertGamesEqual(game1, game2);
    }

    @Test
    public void testSetupPositionRoundTrip() throws PgnFormatException {
        String originalPgn = """
                [Event "Test"]
                [Site "?"]
                [Date "????.??.??"]
                [Round "?"]
                [White "?"]
                [Black "?"]
                [Result "*"]
                [SetUp "1"]
                [FEN "rnbqkbnr/pp1ppppp/8/2p5/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq - 1 2"]

                2... Nc6 3. Bb5 *
                """;

        GameModel game1 = new PgnParser().parseGame(originalPgn);
        PgnExporter exporter = new PgnExporter();
        String exportedPgn = exporter.exportGame(game1);
        GameModel game2 = new PgnParser().parseGame(exportedPgn);

        // Verify setup position is preserved
        assertTrue(game2.moves().isSetupPosition());
        assertEquals(game1.moves().root().position(), game2.moves().root().position());
        assertEquals(game1.moves().countPly(false), game2.moves().countPly(false));
    }

    @Test
    public void testExportMultipleGames() throws IOException, PgnFormatException {
        // Create two very small games
        String inputPgn = """
                [Event "Game 1"]
                [Site "Location 1"]
                [Date "2024.01.01"]
                [Round "1"]
                [White "Player A"]
                [Black "Player B"]
                [Result "1-0"]

                1. e4 e5 2. Nf3 1-0

                [Event "Game 2"]
                [Site "Location 2"]
                [Date "2024.01.02"]
                [Round "2"]
                [White "Player C"]
                [Black "Player D"]
                [Result "0-1"]

                1. d4 d5 0-1
                """;

        // Parse the games
        Stream<GameModel> games = new PgnParser().parseGames(new StringReader(inputPgn));

        // Export using exportGames
        StringWriter writer = new StringWriter();
        PgnExporter exporter = new PgnExporter();
        exporter.exportGames(games, writer);

        // Expected output with exact whitespace
        // Note: Default exporter includes PlyCount and each game ends with single newline
        String expectedOutput = """
                [Event "Game 1"]
                [Site "Location 1"]
                [Date "2024.01.01"]
                [Round "1"]
                [White "Player A"]
                [Black "Player B"]
                [Result "1-0"]
                [PlyCount "3"]

                1. e4 e5 2. Nf3 1-0

                [Event "Game 2"]
                [Site "Location 2"]
                [Date "2024.01.02"]
                [Round "2"]
                [White "Player C"]
                [Black "Player D"]
                [Result "0-1"]
                [PlyCount "2"]

                1. d4 d5 0-1
                """;

        // Compare exactly
        assertEquals(expectedOutput, writer.toString());
    }

    /**
     * Generates a random legal game.
     */
    private GameModel generateRandomGame(Random random, int maxMoves) {
        GameModel game = new GameModel();
        game.header().setEvent("Random Game");
        game.header().setWhite("Random Player 1");
        game.header().setBlack("Random Player 2");

        GameMovesModel.Node node = game.moves().root();
        int moveCount = 0;

        while (moveCount < maxMoves) {
            List<Move> legalMoves = node.position().generateAllLegalMoves();

            if (legalMoves.isEmpty()) {
                // Game ended (checkmate or stalemate)
                break;
            }

            // Pick a random legal move
            Move randomMove = legalMoves.get(random.nextInt(legalMoves.size()));
            node = node.addMove(randomMove);
            moveCount++;

            // Stop if position repeats too many times (to avoid infinite games)
            if (moveCount > 10 && random.nextInt(100) < 5) {
                break; // 5% chance to stop after 10 moves
            }
        }

        return game;
    }

    /**
     * Asserts that two games are equal by comparing their move trees.
     */
    private void assertGamesEqual(GameModel game1, GameModel game2) {
        // Compare ply counts
        assertEquals(
                "Main line ply count mismatch",
                game1.moves().countPly(false),
                game2.moves().countPly(false));

        assertEquals(
                "Total ply count mismatch (including variations)",
                game1.moves().countPly(true),
                game2.moves().countPly(true));

        // Compare tree structure
        assertNodesEqual(game1.moves().root(), game2.moves().root());
    }

    /**
     * Recursively compares two nodes and their children.
     */
    private void assertNodesEqual(GameMovesModel.Node node1, GameMovesModel.Node node2) {
        // Compare move
        if (node1.lastMove() == null) {
            assertNull("Node2 should be root", node2.lastMove());
        } else {
            assertNotNull("Node2 should have a move", node2.lastMove());
            assertEquals("Moves should match", node1.lastMove(), node2.lastMove());
        }

        // Compare position
        assertEquals("Positions should match", node1.position(), node2.position());

        // Compare ply
        assertEquals("Ply should match", node1.ply(), node2.ply());

        // Compare number of children
        assertEquals(
                "Number of variations should match at ply " + node1.ply(),
                node1.numMoves(),
                node2.numMoves());

        // Recursively compare all children
        for (int i = 0; i < node1.numMoves(); i++) {
            assertNodesEqual(node1.children().get(i), node2.children().get(i));
        }
    }
}

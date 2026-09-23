package se.yarin.chess;

import org.junit.Test;
import se.yarin.chess.annotations.NAGAnnotation;
import se.yarin.chess.annotations.CommentaryAfterMoveAnnotation;

import static org.junit.Assert.*;

/**
 * Tests for GameModelComparator, specifically for annotation comparison.
 */
public class GameModelComparatorTest {

    @Test
    public void testIdenticalGamesAreEqual() {
        GameModel game1 = createSimpleGame();
        GameModel game2 = createSimpleGame();

        GameModelComparator.ComparisonResult result = GameModelComparator.compare(game1, game2);
        assertTrue("Identical games should be equal", result.isIdentical());
    }

    @Test
    public void testAnnotationOrderDoesNotMatter() {
        // Create game 1 with annotations in one order
        GameModel game1 = new GameModel();
        game1.header().setWhite("Player 1");
        game1.header().setBlack("Player 2");

        GameMovesModel.Node e4_1 = game1.moves().root().addMove(new ShortMove(Chess.E2, Chess.E4));
        e4_1.addAnnotation(new NAGAnnotation(NAG.GOOD_MOVE));
        e4_1.addAnnotation(new CommentaryAfterMoveAnnotation("Best move"));

        // Create game 2 with same annotations in different order
        GameModel game2 = new GameModel();
        game2.header().setWhite("Player 1");
        game2.header().setBlack("Player 2");

        GameMovesModel.Node e4_2 = game2.moves().root().addMove(new ShortMove(Chess.E2, Chess.E4));
        e4_2.addAnnotation(new CommentaryAfterMoveAnnotation("Best move"));
        e4_2.addAnnotation(new NAGAnnotation(NAG.GOOD_MOVE));

        GameModelComparator.ComparisonResult result = GameModelComparator.compare(game1, game2);
        assertTrue("Annotation order should not matter", result.isIdentical());
    }

    @Test
    public void testDuplicateAnnotationsAreCounted() {
        // Create game 1 with duplicate NAG annotation
        GameModel game1 = new GameModel();
        game1.header().setWhite("Player 1");
        game1.header().setBlack("Player 2");

        GameMovesModel.Node e4_1 = game1.moves().root().addMove(new ShortMove(Chess.E2, Chess.E4));
        e4_1.addAnnotation(new NAGAnnotation(NAG.GOOD_MOVE));
        e4_1.addAnnotation(new NAGAnnotation(NAG.GOOD_MOVE)); // Duplicate

        // Create game 2 with single NAG annotation
        GameModel game2 = new GameModel();
        game2.header().setWhite("Player 1");
        game2.header().setBlack("Player 2");

        GameMovesModel.Node e4_2 = game2.moves().root().addMove(new ShortMove(Chess.E2, Chess.E4));
        e4_2.addAnnotation(new NAGAnnotation(NAG.GOOD_MOVE));

        GameModelComparator.ComparisonResult result = GameModelComparator.compare(game1, game2);
        assertFalse("Duplicate annotations should be counted separately", result.isIdentical());
        assertTrue("Should have annotation differences", result.annotationsMatch() == false);
    }

    @Test
    public void testDuplicateAnnotationsInBothGamesAreEqual() {
        // Create game 1 with duplicate NAG annotations
        GameModel game1 = new GameModel();
        game1.header().setWhite("Player 1");
        game1.header().setBlack("Player 2");

        GameMovesModel.Node e4_1 = game1.moves().root().addMove(new ShortMove(Chess.E2, Chess.E4));
        e4_1.addAnnotation(new NAGAnnotation(NAG.GOOD_MOVE));
        e4_1.addAnnotation(new NAGAnnotation(NAG.GOOD_MOVE)); // Duplicate

        // Create game 2 with same duplicate NAG annotations
        GameModel game2 = new GameModel();
        game2.header().setWhite("Player 1");
        game2.header().setBlack("Player 2");

        GameMovesModel.Node e4_2 = game2.moves().root().addMove(new ShortMove(Chess.E2, Chess.E4));
        e4_2.addAnnotation(new NAGAnnotation(NAG.GOOD_MOVE));
        e4_2.addAnnotation(new NAGAnnotation(NAG.GOOD_MOVE)); // Duplicate

        GameModelComparator.ComparisonResult result = GameModelComparator.compare(game1, game2);
        assertTrue("Games with same duplicate annotations should be equal", result.isIdentical());
    }

    @Test
    public void testDifferentAnnotationsAreDetected() {
        // Create game 1
        GameModel game1 = new GameModel();
        game1.header().setWhite("Player 1");
        game1.header().setBlack("Player 2");

        GameMovesModel.Node e4_1 = game1.moves().root().addMove(new ShortMove(Chess.E2, Chess.E4));
        e4_1.addAnnotation(new NAGAnnotation(NAG.GOOD_MOVE));

        // Create game 2 with different annotation
        GameModel game2 = new GameModel();
        game2.header().setWhite("Player 1");
        game2.header().setBlack("Player 2");

        GameMovesModel.Node e4_2 = game2.moves().root().addMove(new ShortMove(Chess.E2, Chess.E4));
        e4_2.addAnnotation(new NAGAnnotation(NAG.BAD_MOVE));

        GameModelComparator.ComparisonResult result = GameModelComparator.compare(game1, game2);
        assertFalse("Different annotations should be detected", result.isIdentical());
        assertFalse("Should have annotation differences", result.annotationsMatch());
    }

    private GameModel createSimpleGame() {
        GameModel game = new GameModel();
        game.header().setWhite("Player 1");
        game.header().setBlack("Player 2");
        game.header().setResult(GameResult.WHITE_WINS);

        GameMovesModel.Node node = game.moves().root();
        node = node.addMove(new ShortMove(Chess.E2, Chess.E4));
        node.addAnnotation(new NAGAnnotation(NAG.GOOD_MOVE));

        return game;
    }
}

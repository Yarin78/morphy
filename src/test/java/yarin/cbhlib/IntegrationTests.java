package yarin.cbhlib;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import yarin.cbhlib.annotations.Annotation;
import yarin.cbhlib.annotations.SymbolAnnotation;
import yarin.cbhlib.annotations.TextAfterMoveAnnotation;
import yarin.cbhlib.annotations.TextBeforeMoveAnnotation;
import yarin.cbhlib.exceptions.CBHException;
import yarin.cbhlib.exceptions.CBHFormatException;
import yarin.chess.Game;
import yarin.chess.GamePosition;

import java.io.IOException;

public class IntegrationTests {

    private Database db;

    @Before
    public void openTestDatabase() throws IOException, CBHFormatException {
        db = Database.open("src/test/java/yarin/cbhlib/databases/cbhlib_test.cbh");
    }

    @After
    public void closeTestDatabase() {
        // TODO
    }

    @Test
    public void dbStatsIsOk() {
        // Don't make exact checks since the test database is growing
        Assert.assertTrue(db.getNumberOfGames() > 0);
        Assert.assertTrue(db.getNumberOfPlayers() > 0);
    }

    @Test
    public void simpleGame() throws IOException, CBHException {
        // Checks that the moves in a simple game is recorded correctly
        GameHeader gameHeader = db.getGameHeader(1);
        Assert.assertEquals("Simple game", gameHeader.getWhitePlayer().getLastName());
        Assert.assertEquals("1-0", gameHeader.getResult());

        Game game = gameHeader.getGame();
        Assert.assertFalse(game.isSetupPosition());
        Assert.assertFalse(game.isEndOfVariation());

        GamePosition currentPosition = game;
        Assert.assertEquals("e2-e4", currentPosition.getMainMove().toString());
        currentPosition = currentPosition.moveForward();
        Assert.assertEquals("e7-e5", currentPosition.getMainMove().toString());
        currentPosition = currentPosition.moveForward();
        Assert.assertEquals("Bf1-c4", currentPosition.getMainMove().toString());
        currentPosition = currentPosition.moveForward();
        Assert.assertEquals("Nb8-c6", currentPosition.getMainMove().toString());
        currentPosition = currentPosition.moveForward();
        Assert.assertEquals("Qd1-h5", currentPosition.getMainMove().toString());
        currentPosition = currentPosition.moveForward();
        Assert.assertEquals("Ng8-f6", currentPosition.getMainMove().toString());
        currentPosition = currentPosition.moveForward();
        Assert.assertEquals("Qh5xf7", currentPosition.getMainMove().toString());
        currentPosition = currentPosition.moveForward();
        Assert.assertTrue(currentPosition.isEndOfVariation());
    }

    @Test
    public void testBasicGameMetadata() throws IOException, CBHException {
        // Checks that "all" different metadata set for a game is correct
        GameHeader gameHeader = db.getGameHeader(2);
        Assert.assertEquals("Mårdell", gameHeader.getWhitePlayer().getLastName());
        Assert.assertEquals("Jimmy", gameHeader.getWhitePlayer().getFirstName());
        Assert.assertEquals("Doe", gameHeader.getBlackPlayer().getLastName());
        Assert.assertEquals("John", gameHeader.getBlackPlayer().getFirstName());

        Assert.assertEquals("compensation", gameHeader.getResult());
        Assert.assertEquals("Swedish Ch", gameHeader.getTournament().getTitle());

        Assert.assertEquals("A00", gameHeader.getECO());
        Assert.assertEquals(2150, gameHeader.getWhiteElo());
        Assert.assertEquals(2000, gameHeader.getBlackElo());
        Assert.assertEquals(1, gameHeader.getRound());
        Assert.assertEquals(2, gameHeader.getSubRound());
        Assert.assertEquals(new Date(2016, 5, 9), gameHeader.getPlayedDate());

        Assert.assertEquals("SK Rockaden Umeå", gameHeader.getWhiteTeam().getTitle());
        Assert.assertEquals("Test Team", gameHeader.getBlackTeam().getTitle());
        Assert.assertEquals("Test source", gameHeader.getSource().getTitle());
        Assert.assertEquals("Jimmy", gameHeader.getAnnotator().getName());

        Assert.assertEquals(new RatingDetails(true, RatingDetails.RatingType.Normal, 0), gameHeader.getWhiteRatingDetails());
        Assert.assertEquals(new RatingDetails(false, RatingDetails.RatingType.Blitz, 0x86), gameHeader.getBlackRatingDetails());

//        Assert.assertEquals("Test title", gameHeader.getGameTitle()); // Languages?
//        Assert.assertEquals("German title", gameHeader.getGameTitle()); // Languages?
    }


    @Test
    public void testSimpleAnnotations() throws IOException, CBHException {
        // Checks that the basic annotations work
        GameHeader gameHeader = db.getGameHeader(3);

        AnnotatedGame game = gameHeader.getGame();

        GamePosition position = game;
        Annotation a = game.getAnnotations(position).get(0);
        Assert.assertTrue(a instanceof SymbolAnnotation);
        Assert.assertEquals(MoveComment.GoodMove, ((SymbolAnnotation) a).getMoveComment());

        position = position.moveForward();
        a = game.getAnnotations(position).get(0);
        Assert.assertTrue(a instanceof SymbolAnnotation);
        Assert.assertEquals(MoveComment.BadMove, ((SymbolAnnotation) a).getMoveComment());

        position = position.moveForward();
        a = game.getAnnotations(position).get(0);
        Assert.assertTrue(a instanceof SymbolAnnotation);
        Assert.assertEquals(MoveComment.ZugZwang2, ((SymbolAnnotation) a).getMoveComment());

        position = position.moveForward();
        a = game.getAnnotations(position).get(0);
        Assert.assertTrue(a instanceof SymbolAnnotation);
        Assert.assertEquals(MoveComment.OnlyMove, ((SymbolAnnotation) a).getMoveComment());

        position = position.moveForward();
        a = game.getAnnotations(position).get(0);
        Assert.assertTrue(a instanceof SymbolAnnotation);
        Assert.assertEquals(LineEvaluation.Unclear, ((SymbolAnnotation) a).getPositionEval());

        position = position.moveForward();
        a = game.getAnnotations(position).get(0);
        Assert.assertTrue(a instanceof SymbolAnnotation);
        Assert.assertEquals(LineEvaluation.WithInitiative, ((SymbolAnnotation) a).getPositionEval());

        position = position.moveForward();
        a = game.getAnnotations(position).get(0);
        Assert.assertTrue(a instanceof TextAfterMoveAnnotation);
        Assert.assertEquals(" Capture", a.getPostText());

        position = position.moveForward();
        position = position.moveForward();
        position = position.moveForward();
        a = game.getAnnotations(position).get(0);
        Assert.assertTrue(a instanceof TextBeforeMoveAnnotation);
        Assert.assertEquals("Fianchetto ", a.getPreText());

        position = position.moveForward();
        a = game.getAnnotations(position).get(0);
        Assert.assertTrue(a instanceof SymbolAnnotation);
        Assert.assertEquals(LineEvaluation.WhiteHasDecisiveAdvantage, ((SymbolAnnotation) a).getPositionEval());

        position = position.moveForward();
        a = game.getAnnotations(position).get(0);
        Assert.assertTrue(a instanceof SymbolAnnotation);
        Assert.assertEquals(MovePrefix.BetterIs, ((SymbolAnnotation) a).getMovePrefix());

        /*
        position = game;
        while (!position.isEndOfVariation()) {
            for (Annotation a2 : game.getAnnotations(position)){
                if (a2.getPreText() != null) System.out.print(a2.getPreText() + " ");
            }
            System.out.print(position.getMainMove());
            for (Annotation a2 : game.getAnnotations(position)){
                if (a2.getPostText() != null) System.out.print(" " + a2.getPostText());
            }
            System.out.println();

            position = position.moveForward();
        }
        */
    }


    @Test
    public void testDeletedGame() {
        // Test that a game is marked correctly as deleted
    }

    @Test
    public void testTournamentDetails() {
        // Test that the tournament details are read correctly
    }

    @Test
    public void testSourceDetails() {
        // Test that the details of a source is read correctly
    }

    // Test variants
    // Test annotations
    // Test setup position
    // Test null move
    // Guiding texts
}

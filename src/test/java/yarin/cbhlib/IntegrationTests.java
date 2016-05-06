package yarin.cbhlib;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import yarin.cbhlib.exceptions.CBHException;
import yarin.chess.Game;
import yarin.chess.GamePosition;

import java.io.IOException;

/**
 * Created by yarin on 06/05/16.
 */
public class IntegrationTests {

    private Database db;

    @Before
    public void openTestDatabase() throws IOException {
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
}

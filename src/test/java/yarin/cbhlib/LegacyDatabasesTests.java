package yarin.cbhlib;

import org.junit.Assert;
import org.junit.Test;
import yarin.cbhlib.exceptions.CBHException;
import yarin.chess.Game;
import yarin.chess.GamePosition;

import java.io.IOException;

/**
 * Smaller tests to verify that we can read from databases created by earlier versions of ChessBase
 */
public class LegacyDatabasesTests {
    @Test
    public void testCB12LegacyDatabase() throws IOException, CBHException {
        // Test that reading from a database created in CB12
        Database db = Database.open("src/test/java/yarin/cbhlib/databases/cb12.cbh");

        GameHeader gameHeader = db.getGameHeader(1);
        Assert.assertEquals("Doe", gameHeader.getWhitePlayer().getLastName());
        Assert.assertEquals("John", gameHeader.getWhitePlayer().getFirstName());
        Assert.assertEquals("Mårdell", gameHeader.getBlackPlayer().getLastName());
        Assert.assertEquals("Jimmy", gameHeader.getBlackPlayer().getFirstName());
        Assert.assertEquals("½-½", gameHeader.getResult());

        Game game = gameHeader.getGame();
        Assert.assertFalse(game.isSetupPosition());
        Assert.assertFalse(game.isEndOfVariation());

        GamePosition currentPosition = game;
        Assert.assertEquals("e2-e4", currentPosition.getMainMove().toString());
        currentPosition = currentPosition.moveForward();
        Assert.assertEquals("c7-c5", currentPosition.getMainMove().toString());
        currentPosition = currentPosition.moveForward();
        Assert.assertEquals("Ng1-f3", currentPosition.getMainMove().toString());
        currentPosition = currentPosition.moveForward();
        Assert.assertEquals("d7-d6", currentPosition.getMainMove().toString());
        currentPosition = currentPosition.moveForward();
        Assert.assertTrue(currentPosition.isEndOfVariation());

        Assert.assertEquals("My tournament", gameHeader.getTournament().getTitle());

        Assert.assertEquals("B50", gameHeader.getECO());
        Assert.assertEquals(2001, gameHeader.getWhiteElo());
        Assert.assertEquals(2002, gameHeader.getBlackElo());
        Assert.assertEquals(new Date(2016, 5, 10), gameHeader.getPlayedDate());

        Assert.assertEquals("TeamA", gameHeader.getWhiteTeam().getTitle());
        Assert.assertEquals("TeamB", gameHeader.getBlackTeam().getTitle());
        Assert.assertEquals("Jimmy", gameHeader.getAnnotator().getName());
    }

}

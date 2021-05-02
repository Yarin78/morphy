package se.yarin.morphy;

import org.junit.Test;
import se.yarin.chess.Date;

import static org.junit.Assert.*;

public class DatabaseReadTransactionTest {
    @Test
    public void getGame() {
        Database database = ResourceLoader.openWorldChDatabase();

        DatabaseReadTransaction txn = new DatabaseReadTransaction(database);
        Game game = txn.getGame(10);

        assertEquals(database, game.database());
        assertEquals(10, game.id());
        assertEquals("Steinitz, William", game.white().getFullName());
        assertEquals(new Date(1886, 02, 26), game.playedDate());
        assertEquals(20, game.tournament().rounds());
        System.out.println(game.getGameHeaderModel());
    }
}

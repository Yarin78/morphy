package se.yarin.morphy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import org.junit.Test;
import se.yarin.morphy.api.AccessMode;
import se.yarin.morphy.api.Database;
import se.yarin.morphy.api.Databases;
import se.yarin.morphy.api.DatabaseFormat;
import se.yarin.morphy.api.GameFetchOptions;
import se.yarin.morphy.model.GameDto;

/** Verifies that the v1 database is reachable and correct through the neutral facade. */
public class DatabaseCbhTest {

  private File worldChCbh() throws IOException {
    File dir = ResourceLoader.materializeStreamPath(DatabaseCbh.class, "database/World-ch");
    return new File(dir, "World-ch.cbh");
  }

  @Test
  public void opensThroughFactoryAsCbh() throws IOException {
    try (Database db = Databases.open(worldChCbh(), AccessMode.READ_ONLY)) {
      assertTrue(db instanceof DatabaseCbh);
      assertEquals(DatabaseFormat.CBH, db.format());
      assertTrue(db.capabilities().canWrite());
      assertTrue(db.count() >= 73);
    }
  }

  @Test
  public void readsAGameAsDto() throws IOException {
    try (Database db = Databases.open(worldChCbh(), AccessMode.READ_ONLY)) {
      GameDto game = db.getGame(73, GameFetchOptions.full());
      assertNotNull(game);
      assertEquals("game", game.type());
      assertNotNull(game.whitePlayer());
      assertEquals("Chigorin", game.whitePlayer().lastName());
      assertEquals("Steinitz", game.blackPlayer().lastName());
      assertNotNull(game.tournament());
      assertEquals("World-ch04 Steinitz-Chigorin +10-8=5", game.tournament().title());
      assertNotNull(game.moves());
      assertNotNull(game.moves().pgn());
    }
  }

  @Test
  public void missingGameIsNull() throws IOException {
    try (Database db = Databases.open(worldChCbh(), AccessMode.READ_ONLY)) {
      assertNull(db.getGame(100000, GameFetchOptions.headersOnly()));
    }
  }
}

package se.yarin.morphy.cb2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import se.yarin.morphy.api.AccessMode;
import se.yarin.morphy.api.Database;
import se.yarin.morphy.api.Databases;
import se.yarin.morphy.api.DatabaseFormat;
import se.yarin.morphy.api.GameFetchOptions;

/** Verifies that the v2 stub is discoverable through the facade and reports its game count. */
class Database2CbhTest {

  private static File sample() {
    // Tests run with the module directory as the working directory.
    File f = new File("../test-databases/wch2/wch2.2cbh");
    assertTrue(f.exists(), "sample .2cbh database not found: " + f.getAbsolutePath());
    return f;
  }

  @Test
  void opensThroughFactoryAndCountsGames() throws IOException {
    try (Database db = Databases.open(sample(), AccessMode.READ_ONLY)) {
      assertInstanceOf(Database2Cbh.class, db);
      assertEquals(DatabaseFormat.CB2, db.format());
      // (199488 - 192) / 192
      assertEquals(1038, db.count());
      assertTrue(db.capabilities().hasEntities());
    }
  }

  @Test
  void gameReadsAreNotImplementedYet() throws IOException {
    try (Database db = Databases.open(sample(), AccessMode.READ_ONLY)) {
      assertThrows(
          UnsupportedOperationException.class,
          () -> db.getGame(1, GameFetchOptions.headersOnly()));
    }
  }
}

package se.yarin.morphy.cb2.moves;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import se.yarin.chess.GameMovesModel;
import se.yarin.morphy.api.AccessMode;
import se.yarin.morphy.cb2.TestDatabases;
import se.yarin.morphy.cb2.games.GameHeaderFile;
import se.yarin.morphy.cb2.games.GameRecord;
import se.yarin.morphy.cb2.games.TextHeader;
import se.yarin.morphy.cb2.storage.ByteStore;
import se.yarin.morphy.cb2.storage.RecordFile;
import se.yarin.morphy.chessbase.text.TextContentsModel;

/** Every game of the sample database decodes, and encodes back to the same bytes. */
class MoveStreamRoundTripTest {

  @Test
  void everyGameRoundTrips() {
    long plies = roundTrip(TestDatabases.wch2());
    assertTrue(plies > 50_000, "only " + plies + " plies");
  }

  @Test
  void scratchDatabasesRoundTrip() {
    for (String name : new String[] {"probe", "reveng1"}) {
      File file = new File("../test-databases/scratch/" + name + ".2cbh");
      Assumptions.assumeTrue(file.exists(), "scratch database not present");
      roundTrip(file);
    }
  }

  private static long roundTrip(File file) {
    try (GameHeaderFile headers = new GameHeaderFile(ByteStore.open(file, AccessMode.READ_ONLY), "cbh");
        RecordFile moves =
            new RecordFile(
                ByteStore.open(TestDatabases.sibling(file, ".2cbg"), AccessMode.READ_ONLY), "cbg")) {
      long plies = 0;
      int texts = 0;
      for (int id = 1; id <= headers.count(); id++) {
        GameRecord record = headers.get(id);
        assertArrayEquals(headers.readRaw(id), record.encode(), "record " + id);
        if (record instanceof TextHeader) {
          RecordFile.Record text = moves.read(record.movesOffset());
          assertEquals(RecordFile.TAG_TEXT, text.tag());
          // A text stored in cp1252 is written back in UTF-8, so only the contents must agree
          TextContentsModel contents = GuidingTextCodec.decode(text.content());
          assertEquals(
              contents.contents(),
              GuidingTextCodec.decode(GuidingTextCodec.encode(contents)).contents(),
              "text " + id);
          texts++;
          continue;
        }
        RecordFile.Record stored = moves.read(record.movesOffset());
        GameMovesModel model = MoveStreamCodec.decode(stored.tag(), stored.content());
        MoveStreamCodec.Encoded encoded = MoveStreamCodec.encode(model);
        assertEquals(stored.tag(), encoded.tag(), "tag of game " + id);
        assertArrayEquals(stored.content(), encoded.content(), "moves of game " + id);
        plies += model.countPly(true);
        if (id == 1) {
          System.out.println(file.getName() + " game 1: " + model);
        }
      }
      System.out.println(file.getName() + ": " + texts + " texts");
      return plies;
    }
  }
}

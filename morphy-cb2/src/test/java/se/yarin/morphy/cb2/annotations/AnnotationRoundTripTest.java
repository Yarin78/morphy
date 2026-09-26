package se.yarin.morphy.cb2.annotations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import se.yarin.chess.GameMovesModel;
import se.yarin.morphy.api.AccessMode;
import se.yarin.morphy.cb2.TestDatabases;
import se.yarin.morphy.cb2.games.GameHeaderFile;
import se.yarin.morphy.cb2.games.GameRecord;
import se.yarin.morphy.cb2.games.TextHeader;
import se.yarin.morphy.cb2.moves.MoveStreamCodec;
import se.yarin.morphy.cb2.storage.ByteStore;
import se.yarin.morphy.cb2.storage.RecordFile;

/**
 * The annotations of every game decode, and encode back to the same annotations. Texts stored in
 * cp1252 are written back in UTF-8, so the bytes needn't be the same.
 */
class AnnotationRoundTripTest {

  @Test
  void sampleDatabaseRoundTrips() {
    Result result = roundTrip(TestDatabases.wch2());
    System.out.println("wch2: " + result);
    assertTrue(result.annotated > 100, "only " + result.annotated + " annotated games");
    assertEquals(List.of(), result.unreadable);
    assertEquals(List.of(), result.different);
  }

  @Test
  void scratchDatabasesRoundTrip() {
    for (String name : new String[] {"probe", "reveng1"}) {
      File file = new File("../test-databases/scratch/" + name + ".2cbh");
      Assumptions.assumeTrue(file.exists(), "scratch database not present");
      Result result = roundTrip(file);
      System.out.println(name + ": " + result);
      assertEquals(List.of(), result.different, name);
    }
  }

  record Result(int annotated, int identical, List<Integer> unreadable, List<Integer> different) {}

  private static Result roundTrip(File file) {
    int annotated = 0, identical = 0;
    List<Integer> unreadable = new ArrayList<>(), different = new ArrayList<>();
    try (GameHeaderFile headers =
            new GameHeaderFile(ByteStore.open(file, AccessMode.READ_ONLY), "cbh");
        RecordFile moves =
            new RecordFile(
                ByteStore.open(TestDatabases.sibling(file, ".2cbg"), AccessMode.READ_ONLY), "cbg");
        RecordFile annotations =
            new RecordFile(
                ByteStore.open(TestDatabases.sibling(file, ".2cba"), AccessMode.READ_ONLY),
                "cba")) {
      for (int id = 1; id <= headers.count(); id++) {
        GameRecord record = headers.get(id);
        if (record instanceof TextHeader) {
          continue;
        }
        RecordFile.Record stored = moves.read(record.movesOffset());
        GameMovesModel model = MoveStreamCodec.decode(stored.tag(), stored.content());
        RecordFile.Record annotationRecord = annotations.read(record.annotationOffset());
        assertEquals(RecordFile.TAG_ANNOTATIONS, annotationRecord.tag());
        byte[] content = annotationRecord.content();
        if (!Arrays.equals(content, AnnotationBlockCodec.EMPTY)) {
          annotated++;
        }
        if (!AnnotationBlockCodec.decode(content, model)) {
          unreadable.add(id);
          continue;
        }
        byte[] encoded = AnnotationBlockCodec.encode(model);
        if (Arrays.equals(content, encoded)) {
          identical++;
        } else if (!AnnotationBlockCodec.read(content).equals(AnnotationBlockCodec.read(encoded))) {
          // Texts stored in cp1252 are written back in UTF-8, so only the annotations must agree
          different.add(id);
        }
      }
    }
    return new Result(annotated, identical, unreadable, different);
  }
}

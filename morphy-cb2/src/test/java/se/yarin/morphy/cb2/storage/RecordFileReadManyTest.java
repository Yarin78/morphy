package se.yarin.morphy.cb2.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import se.yarin.morphy.api.AccessMode;
import se.yarin.morphy.cb2.TestDatabases;
import se.yarin.morphy.cb2.games.GameHeaderFile;
import se.yarin.morphy.cb2.games.GameRecord;

/** Records read many at a time are those read one by one, in whatever order they're asked for. */
class RecordFileReadManyTest {

  @Test
  void manyRecordsReadAtOnceAreTheSameAsReadOneByOne() {
    File file = TestDatabases.wch2();
    try (GameHeaderFile headers = new GameHeaderFile(ByteStore.open(file, AccessMode.READ_ONLY), "cbh");
        RecordFile moves =
            new RecordFile(
                ByteStore.open(TestDatabases.sibling(file, ".2cbg"), AccessMode.READ_ONLY), "cbg")) {
      GameRecord[] records = headers.read(1, headers.count());
      assertEquals(headers.count(), records.length);
      List<Long> offsets = new ArrayList<>();
      for (int id = 1; id <= records.length; id++) {
        assertArrayEquals(headers.readRaw(id), records[id - 1].raw(), "record " + id);
        offsets.add(records[id - 1].movesOffset());
      }
      // In id order, as a scan asks for them, and shuffled
      check(moves, offsets);
      Collections.shuffle(offsets, new Random(1));
      check(moves, offsets.subList(0, offsets.size() / 3));
    }
  }

  private static void check(RecordFile moves, List<Long> offsets) {
    long[] array = offsets.stream().mapToLong(Long::longValue).toArray();
    RecordFile.Record[] many = moves.readMany(array);
    for (int i = 0; i < array.length; i++) {
      assertEquals(moves.read(array[i]), many[i], "record at " + array[i]);
    }
  }
}

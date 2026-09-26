package se.yarin.morphy.cb2.indexes;

import java.io.File;
import java.io.IOException;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import se.yarin.morphy.api.AccessMode;
import se.yarin.morphy.cb2.Consistency;
import se.yarin.morphy.cb2.Database2Cbh;
import se.yarin.morphy.cb2.TestDatabases;

/** The index files of the sample databases agree with the games and entities. */
class IndexConsistencyTest {

  @Test
  void sampleDatabase() throws IOException {
    try (Database2Cbh db = Database2Cbh.open(TestDatabases.wch2(), AccessMode.READ_ONLY)) {
      Consistency.check(db);
    }
  }

  @Test
  void scratchDatabases() throws IOException {
    for (String name : new String[] {"probe", "reveng1"}) {
      File file = new File("../test-databases/scratch/" + name + ".2cbh");
      Assumptions.assumeTrue(file.exists(), "scratch database not present");
      try (Database2Cbh db = Database2Cbh.open(file, AccessMode.READ_ONLY)) {
        Consistency.check(db);
      }
    }
  }
}

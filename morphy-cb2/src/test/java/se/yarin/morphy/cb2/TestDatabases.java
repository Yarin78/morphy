package se.yarin.morphy.cb2;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;

/** The sample databases the tests read. */
public final class TestDatabases {
  private TestDatabases() {}

  /** The World Championship database in the v2 format; tests run in the module directory. */
  public static File wch2() {
    File f = new File("../test-databases/wch2/wch2.2cbh");
    assertTrue(f.exists(), "sample .2cbh database not found: " + f.getAbsolutePath());
    return f;
  }

  /** The same database in the v1 format, with the same game ids. */
  public static File worldCh() {
    File f = new File("../test-databases/world-ch/World-ch.cbh");
    assertTrue(f.exists(), "sample .cbh database not found: " + f.getAbsolutePath());
    return f;
  }

  /** A file next to a database file, with another extension. */
  public static File sibling(File file, String extension) {
    String name = file.getName();
    return new File(file.getParentFile(), name.substring(0, name.lastIndexOf('.')) + extension);
  }
}

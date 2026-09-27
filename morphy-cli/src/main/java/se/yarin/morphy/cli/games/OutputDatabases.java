package se.yarin.morphy.cli.games;

import se.yarin.morphy.DatabaseCbh;
import se.yarin.morphy.cb2.Database2Cbh;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;

/** Shared helpers for CLI commands that create a new output {@code Database} via the facade. */
public final class OutputDatabases {
  private OutputDatabases() {}

  /**
   * If {@code file} exists: deletes it (and its sidecar files, for multi-file formats) when {@code
   * overwrite} is true, or throws when it's false. Does nothing if it doesn't exist. Call before
   * {@code Databases.create(file)}.
   */
  public static void prepareForOverwrite(File file, boolean overwrite) throws IOException {
    if (!file.exists()) {
      return;
    }
    if (!overwrite) {
      throw new FileAlreadyExistsException(file.toString());
    }
    String name = file.getName().toLowerCase();
    if (name.endsWith(".cbh")) {
      DatabaseCbh.delete(file);
    } else if (name.endsWith(".2cbh")) {
      Database2Cbh.delete(file);
    } else {
      // TODO: A pgn database may have additional index files that should be deleted as well
      file.delete();
    }
  }
}

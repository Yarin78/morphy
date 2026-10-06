package se.yarin.morphy.positions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jetbrains.annotations.NotNull;

/**
 * What tells whether a database has changed since its index was built: its main file's size and
 * time of change, and its number of games. Every change of a game rewrites its record in the
 * main file.
 *
 * @param size the main file's size
 * @param modified when the main file was last changed, in milliseconds
 * @param gameCount the number of game records
 */
public record DatabaseIdentity(long size, long modified, long gameCount) {

  public static @NotNull DatabaseIdentity of(@NotNull Path databaseFile, long gameCount)
      throws IOException {
    return new DatabaseIdentity(
        Files.size(databaseFile),
        Files.getLastModifiedTime(databaseFile).toMillis(),
        gameCount);
  }
}

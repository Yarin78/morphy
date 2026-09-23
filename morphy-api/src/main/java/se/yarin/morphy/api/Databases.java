package se.yarin.morphy.api;

import java.io.File;
import java.util.ServiceLoader;
import org.jetbrains.annotations.NotNull;

/**
 * Entry point for opening a {@link Database} of any format. Dispatches to the {@link
 * DatabaseProvider} that recognises the file, discovered through the {@link ServiceLoader}, so
 * that this module needs no compile-time dependency on any concrete format.
 */
public final class Databases {

  private Databases() {}

  /** Opens a database for reading and writing. */
  @NotNull
  public static Database open(@NotNull File file) {
    return open(file, AccessMode.READ_WRITE);
  }

  /**
   * Opens the database rooted at {@code file} in the given mode, using the first registered provider
   * that recognises it.
   *
   * @throws UnsupportedOperationException if no provider handles the file
   */
  @NotNull
  public static Database open(@NotNull File file, @NotNull AccessMode mode) {
    for (DatabaseProvider provider : ServiceLoader.load(DatabaseProvider.class)) {
      if (provider.handles(file)) {
        return provider.open(file, mode);
      }
    }
    throw new UnsupportedOperationException(
        "No chess database provider handles file: " + file.getName());
  }
}

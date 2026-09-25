package se.yarin.morphy.api;

import java.io.File;
import java.io.IOException;
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
  public static Database open(@NotNull File file) throws IOException {
    return open(file, AccessMode.READ_WRITE);
  }

  /**
   * Opens the database rooted at {@code file} in the given mode, using the first registered provider
   * that recognises it.
   *
   * @throws IOException if the database can't be opened
   * @throws UnsupportedOperationException if no provider handles the file
   */
  @NotNull
  public static Database open(@NotNull File file, @NotNull AccessMode mode) throws IOException {
    return providerFor(file).open(file, mode);
  }

  /**
   * Creates a new, empty database rooted at {@code file}, in the format its extension names, and
   * opens it for reading and writing.
   *
   * @throws IOException if the database can't be created
   * @throws UnsupportedOperationException if no provider handles the file, or the format can't be
   *     written
   */
  @NotNull
  public static Database create(@NotNull File file) throws IOException {
    return providerFor(file).create(file);
  }

  private static DatabaseProvider providerFor(@NotNull File file) {
    for (DatabaseProvider provider : ServiceLoader.load(DatabaseProvider.class)) {
      if (provider.handles(file)) {
        return provider;
      }
    }
    throw new UnsupportedOperationException(
        "No chess database provider handles file: " + file.getName());
  }
}

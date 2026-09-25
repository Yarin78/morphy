package se.yarin.morphy.api;

import java.io.File;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;

/**
 * Service-provider interface that lets {@link Databases} open a database without the neutral
 * api module depending on any concrete format module. Each format module (cbh, cb2, pgn) registers
 * one provider via {@code META-INF/services} / a {@code provides} clause in its module descriptor.
 */
public interface DatabaseProvider {

  /** The format this provider opens. */
  @NotNull
  DatabaseFormat format();

  /** Whether this provider recognises {@code file} (typically by extension). */
  boolean handles(@NotNull File file);

  /**
   * Opens the database rooted at {@code file}.
   *
   * @param file the primary database file
   * @param mode how the database should be accessed
   * @throws IOException if the database can't be opened
   */
  @NotNull
  Database open(@NotNull File file, @NotNull AccessMode mode) throws IOException;

  /**
   * Creates a new, empty database rooted at {@code file} and opens it for reading and writing.
   *
   * @param file the primary database file; no database with that name may exist
   * @throws IOException if the database can't be created
   * @throws UnsupportedOperationException if this format can't be written
   */
  @NotNull
  Database create(@NotNull File file) throws IOException;
}

package se.yarin.morphy.api;

import java.io.File;
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
   */
  @NotNull
  Database open(@NotNull File file, @NotNull AccessMode mode);
}

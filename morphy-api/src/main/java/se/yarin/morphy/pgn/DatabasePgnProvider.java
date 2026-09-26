package se.yarin.morphy.pgn;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.api.AccessMode;
import se.yarin.morphy.api.Database;
import se.yarin.morphy.api.DatabaseFormat;
import se.yarin.morphy.api.DatabaseProvider;

/** Opens {@code .pgn} files as a {@link DatabasePgn}. */
public final class DatabasePgnProvider implements DatabaseProvider {

  @Override
  public @NotNull DatabaseFormat format() {
    return DatabaseFormat.PGN;
  }

  @Override
  public boolean handles(@NotNull File file) {
    return file.getName().toLowerCase(Locale.ROOT).endsWith(".pgn");
  }

  @Override
  public @NotNull Database open(@NotNull File file, @NotNull AccessMode mode) throws IOException {
    return DatabasePgn.open(file.toPath(), mode);
  }

  @Override
  public @NotNull Database create(@NotNull File file) throws IOException {
    return DatabasePgn.create(file.toPath());
  }
}

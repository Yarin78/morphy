package se.yarin.morphy;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Locale;
import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.api.AccessMode;
import se.yarin.morphy.api.Database;
import se.yarin.morphy.api.DatabaseProvider;
import se.yarin.morphy.api.DatabaseFormat;

/** Registers the v1 ({@code .cbh}) format with {@link se.yarin.morphy.api.Databases}. */
public class DatabaseCbhProvider implements DatabaseProvider {

  @Override
  public @NotNull DatabaseFormat format() {
    return DatabaseFormat.CBH;
  }

  @Override
  public boolean handles(@NotNull File file) {
    return file.getName().toLowerCase(Locale.ROOT).endsWith(".cbh");
  }

  @Override
  public @NotNull Database open(@NotNull File file, @NotNull AccessMode mode) {
    DatabaseMode dbMode =
        switch (mode) {
          case READ_ONLY -> DatabaseMode.READ_ONLY;
          case READ_WRITE -> DatabaseMode.READ_WRITE;
          case IN_MEMORY -> DatabaseMode.IN_MEMORY;
        };
    try {
      return DatabaseCbh.open(file, dbMode);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to open " + file, e);
    }
  }
}

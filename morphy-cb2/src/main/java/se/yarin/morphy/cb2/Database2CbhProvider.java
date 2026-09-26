package se.yarin.morphy.cb2;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.api.AccessMode;
import se.yarin.morphy.api.Database;
import se.yarin.morphy.api.DatabaseProvider;
import se.yarin.morphy.api.DatabaseFormat;

/** Registers the v2 format with {@link se.yarin.morphy.api.Databases}. */
public class Database2CbhProvider implements DatabaseProvider {

  @Override
  public @NotNull DatabaseFormat format() {
    return DatabaseFormat.CB2;
  }

  @Override
  public boolean handles(@NotNull File file) {
    return file.getName().toLowerCase(Locale.ROOT).endsWith(".2cbh");
  }

  @Override
  public @NotNull Database open(@NotNull File file, @NotNull AccessMode mode) throws IOException {
    return new Database2CbhFacade(Database2Cbh.open(file, mode));
  }

  @Override
  public @NotNull Database create(@NotNull File file) throws IOException {
    return new Database2CbhFacade(Database2Cbh.create(file));
  }
}

package se.yarin.morphy.cb2;

import java.io.File;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.api.AccessMode;
import se.yarin.morphy.api.Capabilities;
import se.yarin.morphy.api.Database;
import se.yarin.morphy.api.DatabaseFormat;
import se.yarin.morphy.api.EntityKind;
import se.yarin.morphy.api.GameFetchOptions;
import se.yarin.morphy.api.query.Query;
import se.yarin.morphy.api.query.ResultPage;
import se.yarin.morphy.api.query.SearchSchema;
import se.yarin.morphy.model.GameDto;

/**
 * A {@link Database} over the ChessBase v2 ({@code .2cbh}) format.
 *
 * <p><b>Stub.</b> This opens the database and reports its game count from the {@code .2cbh} header,
 * which is enough to prove the facade is implementable by a second format and to give the follow-up
 * v2 reader a wired-in target. Every game and entity read still throws {@link
 * UnsupportedOperationException}.
 */
public class Database2Cbh implements Database {

  /** Both the file header and each record are 192 bytes. */
  static final int RECORD_SIZE = 192;

  private final String name;
  private final long count;

  private Database2Cbh(@NotNull String name, long count) {
    this.name = name;
    this.count = count;
  }

  /** Opens a {@code .2cbh} database and reads its game count. */
  @NotNull
  public static Database2Cbh open(@NotNull File file, @NotNull AccessMode mode) {
    long length = file.length();
    // The .2cbh file is a 192-byte header followed by one 192-byte record per game.
    long count = length <= RECORD_SIZE ? 0 : (length - RECORD_SIZE) / RECORD_SIZE;
    return new Database2Cbh(file.getName(), count);
  }

  @Override
  public @NotNull String name() {
    return name;
  }

  @Override
  public @NotNull DatabaseFormat format() {
    return DatabaseFormat.CB2;
  }

  @Override
  public @NotNull Capabilities capabilities() {
    // Reads are not implemented yet; writing is not planned for the first version of the reader.
    return new Capabilities(false, true, false, false);
  }

  @Override
  public long gameCount() {
    return count;
  }

  private static UnsupportedOperationException notYet() {
    return new UnsupportedOperationException("The v2 (.2cbh) reader is not implemented yet");
  }

  @Override
  public @Nullable GameDto getGame(long id, @NotNull GameFetchOptions fetch) {
    throw notYet();
  }

  @Override
  public @NotNull ResultPage<GameDto> findGames(
      @NotNull Query query, @NotNull GameFetchOptions fetch) {
    throw notYet();
  }

  @Override
  public @NotNull SearchSchema gameSearchSchema() {
    throw notYet();
  }

  @Override
  public long addGame(@NotNull GameDto game) {
    throw new UnsupportedOperationException("Database " + name + " is read-only");
  }

  @Override
  public void replaceGame(long id, @NotNull GameDto game) {
    throw new UnsupportedOperationException("Database " + name + " is read-only");
  }

  @Override
  public long entityCount(@NotNull EntityKind<?> kind) {
    throw notYet();
  }

  @Override
  public <T> @Nullable T getEntity(@NotNull EntityKind<T> kind, long id) {
    throw notYet();
  }

  @Override
  public <T> @NotNull ResultPage<T> findEntities(
      @NotNull EntityKind<T> kind, @NotNull Query query) {
    throw notYet();
  }

  @Override
  public @NotNull SearchSchema entitySearchSchema(@NotNull EntityKind<?> kind) {
    throw notYet();
  }

  @Override
  public <T> @NotNull T updateEntity(@NotNull EntityKind<T> kind, long id, @NotNull T entity) {
    throw new UnsupportedOperationException("Database " + name + " is read-only");
  }

  @Override
  public void close() {
    // Nothing held open yet.
  }
}

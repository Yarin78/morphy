package se.yarin.morphy.cb2;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.api.AccessMode;
import se.yarin.morphy.api.Capabilities;
import se.yarin.morphy.api.Database;
import se.yarin.morphy.api.DatabaseFormat;
import se.yarin.morphy.api.EntityKind;
import se.yarin.morphy.api.GameFetchOptions;
import se.yarin.morphy.model.AnnotatorDto;
import se.yarin.morphy.model.GameDto;
import se.yarin.morphy.model.GameTagDto;
import se.yarin.morphy.model.PlayerDto;
import se.yarin.morphy.model.SourceDto;
import se.yarin.morphy.model.TeamDto;
import se.yarin.morphy.model.TournamentDto;

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
  private final int count;

  private Database2Cbh(@NotNull String name, int count) {
    this.name = name;
    this.count = count;
  }

  /** Opens a {@code .2cbh} database and reads its game count. */
  @NotNull
  public static Database2Cbh open(@NotNull File file, @NotNull AccessMode mode) {
    long length;
    try {
      length = file.length();
    } catch (SecurityException e) {
      throw new UncheckedIOException(new IOException("Cannot read " + file, e));
    }
    // The .2cbh file is a 192-byte header followed by one 192-byte record per game.
    int count = length <= RECORD_SIZE ? 0 : (int) ((length - RECORD_SIZE) / RECORD_SIZE);
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
    // Reads are not implemented yet, but advertise the eventual shape: read-only for now.
    return Capabilities.readOnlyWithEntities();
  }

  @Override
  public int count() {
    return count;
  }

  private static UnsupportedOperationException notYet() {
    return new UnsupportedOperationException("The v2 (.2cbh) reader is not implemented yet");
  }

  @Override
  public @Nullable GameDto getGame(int gameId, @NotNull GameFetchOptions options) {
    throw notYet();
  }

  @Override
  public void forEachGame(
      @Nullable Integer startId,
      @Nullable Integer endId,
      @NotNull GameFetchOptions options,
      @NotNull Consumer<GameDto> consumer) {
    throw notYet();
  }

  @Override
  public long entityCount(@NotNull EntityKind kind) {
    throw notYet();
  }

  @Override
  public @Nullable PlayerDto getPlayer(long id, boolean includeRawData) {
    throw notYet();
  }

  @Override
  public @Nullable TournamentDto getTournament(
      long id, boolean includeDetails, boolean includeRawData) {
    throw notYet();
  }

  @Override
  public @Nullable AnnotatorDto getAnnotator(long id, boolean includeRawData) {
    throw notYet();
  }

  @Override
  public @Nullable SourceDto getSource(long id, boolean includeDetails, boolean includeRawData) {
    throw notYet();
  }

  @Override
  public @Nullable TeamDto getTeam(long id, boolean includeDetails, boolean includeRawData) {
    throw notYet();
  }

  @Override
  public @Nullable GameTagDto getGameTag(long id, boolean includeRawData) {
    throw notYet();
  }

  @Override
  public int addGame(@NotNull GameDto game) {
    throw notYet();
  }

  @Override
  public void replaceGame(int gameId, @NotNull GameDto game) {
    throw notYet();
  }

  @Override
  public void close() {
    // Nothing held open yet.
  }
}

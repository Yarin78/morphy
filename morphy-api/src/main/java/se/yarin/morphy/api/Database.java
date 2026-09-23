package se.yarin.morphy.api;

import java.io.IOException;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.model.AnnotatorDto;
import se.yarin.morphy.model.GameDto;
import se.yarin.morphy.model.GameTagDto;
import se.yarin.morphy.model.PlayerDto;
import se.yarin.morphy.model.SourceDto;
import se.yarin.morphy.model.TeamDto;
import se.yarin.morphy.model.TournamentDto;

/**
 * The vendor-neutral view of a chess database, whatever its on-disk format.
 *
 * <p>A database holds games (and guiding texts) addressed by a 1-based game id, and the entities
 * those games refer to. Everything crossing this interface is a {@link se.yarin.morphy.model DTO}:
 * the format-specific storage classes never leak out. Concrete implementations are {@code
 * CbhDatabase} (v1), {@code Database2Cbh} (v2) and a PGN adapter.
 *
 * <p>Rich search (filtering, joins, sorting) is intentionally <b>not</b> part of this interface for
 * now; it remains specific to the v1 query engine. This facade covers open/close, game and entity
 * reads producing DTOs, and DTO-based writes.
 *
 * <p>Obtain an instance through {@link Databases#open}.
 */
public interface Database extends AutoCloseable {

  /** A human-readable name, usually derived from the file name. */
  @NotNull
  String name();

  /** The concrete format backing this database. */
  @NotNull
  DatabaseFormat format();

  /** What this implementation supports; guards optional operations. */
  @NotNull
  Capabilities capabilities();

  /** The number of game records, including deleted ones and guiding texts. */
  int count();

  /**
   * Fetches a single game (or guiding text) as a DTO.
   *
   * @param gameId the 1-based game id
   * @param options how much of the game to materialise
   * @return the game, or {@code null} if no such game exists
   */
  @Nullable
  GameDto getGame(int gameId, @NotNull GameFetchOptions options);

  /**
   * Passes every game with an id in {@code [startId, endId)} to {@code consumer}, in id order,
   * within a single read transaction. A {@code null} bound means open-ended.
   *
   * @param startId first id, inclusive; {@code null} for the first game
   * @param endId last id, exclusive; {@code null} for past the last game
   * @param options how much of each game to materialise
   * @param consumer receives each game DTO
   */
  void forEachGame(
      @Nullable Integer startId,
      @Nullable Integer endId,
      @NotNull GameFetchOptions options,
      @NotNull Consumer<GameDto> consumer);

  // ── Entities ──────────────────────────────────────────────────────────────

  /** The number of entities of the given kind. */
  long entityCount(@NotNull EntityKind kind);

  @Nullable
  PlayerDto getPlayer(long id, boolean includeRawData);

  @Nullable
  TournamentDto getTournament(long id, boolean includeDetails, boolean includeRawData);

  @Nullable
  AnnotatorDto getAnnotator(long id, boolean includeRawData);

  @Nullable
  SourceDto getSource(long id, boolean includeDetails, boolean includeRawData);

  @Nullable
  TeamDto getTeam(long id, boolean includeDetails, boolean includeRawData);

  @Nullable
  GameTagDto getGameTag(long id, boolean includeRawData);

  // ── Writes (only when capabilities().canWrite()) ────────────────────────────

  /**
   * Adds a game from a DTO.
   *
   * @return the id assigned to the new game
   * @throws UnsupportedOperationException if this database is read-only
   */
  int addGame(@NotNull GameDto game);

  /**
   * Replaces an existing game with the contents of a DTO.
   *
   * @throws UnsupportedOperationException if this database is read-only
   */
  void replaceGame(int gameId, @NotNull GameDto game);

  @Override
  void close() throws IOException;
}

package se.yarin.morphy.api;

import java.io.IOException;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.api.query.Query;
import se.yarin.morphy.api.query.ResultPage;
import se.yarin.morphy.api.query.SearchSchema;
import se.yarin.morphy.model.GameDto;

/**
 * The vendor-neutral view of a chess database, whatever its on-disk format.
 *
 * <p>A database holds games (and guiding texts) addressed by a 1-based game id, and the entities
 * those games refer to: players, tournaments, annotators, sources, teams and game tags.
 * Everything crossing this interface is a {@link se.yarin.morphy.model DTO}; the format-specific
 * storage classes never leak out. Implementations are {@code DatabaseCbh} (v1) and {@code
 * Database2Cbh} (v2); obtain one through {@link Databases#open}.
 *
 * <h2>Searching</h2>
 *
 * Games and entities are found with a {@link Query}: a filter in the filter language, a sort order
 * and an offset/limit window. An unfiltered query lists everything, page by page. Which fields
 * can be filtered and sorted on is described by {@link #gameSearchSchema()} and {@link
 * #entitySearchSchema(EntityKind)}; an unknown field is rejected with an {@link
 * IllegalArgumentException}.
 *
 * <h2>Entities exist only through games</h2>
 *
 * An entity that no game refers to does not exist as far as this interface is concerned: {@link
 * #getEntity} returns null for it, and {@link #findEntities} and {@link #entityCount} leave it out.
 * The entity methods are only available when {@link Capabilities#hasEntities()}.
 *
 * <h2>Format-specific extensions</h2>
 *
 * Functionality that only makes sense for one format, such as access to raw record bytes, is not
 * part of this interface. An implementation can offer it as an extension interface, reached
 * through {@link #extension(Class)}.
 */
public interface Database extends AutoCloseable {

  /** A human-readable name, usually derived from the file name. */
  @NotNull
  String name();

  /** The concrete format backing this database. */
  @NotNull
  DatabaseFormat format();

  /** What this database supports; guards the optional operations. */
  @NotNull
  Capabilities capabilities();

  // ── Games ────────────────────────────────────────────────────────────────

  /** The number of game records, including deleted games and guiding texts. */
  long gameCount();

  /**
   * Fetches a single game (or guiding text).
   *
   * @param id the 1-based game id
   * @param fetch how much of the game to materialise
   * @return the game, or null if there is no game with that id
   */
  @Nullable
  GameDto getGame(long id, @NotNull GameFetchOptions fetch);

  /**
   * Finds the games matching a query.
   *
   * @param query the filter, sort order and window
   * @param fetch how much of each game to materialise
   * @return one page of matching games
   * @throws IllegalArgumentException if the query uses an unknown filter or sort field, or an
   *     invalid value
   */
  @NotNull
  ResultPage<GameDto> findGames(@NotNull Query query, @NotNull GameFetchOptions fetch);

  /** The fields games can be filtered and sorted on in this database. */
  @NotNull
  SearchSchema gameSearchSchema();

  /**
   * Adds a game. Entities the game refers to by id must exist; entities it refers to only by name
   * are found, or created if they don't exist.
   *
   * @return the id of the new game
   * @throws UnsupportedOperationException if the database can't be written
   */
  long addGame(@NotNull GameDto game);

  /**
   * Replaces an existing game, resolving its entities as {@link #addGame} does.
   *
   * @throws UnsupportedOperationException if the database can't be written
   * @throws IllegalArgumentException if there is no game with that id
   */
  void replaceGame(long id, @NotNull GameDto game);

  // ── Entities (only if capabilities().hasEntities()) ─────────────────────

  /** The number of entities of a kind that at least one game refers to. */
  long entityCount(@NotNull EntityKind<?> kind);

  /**
   * Fetches a single entity.
   *
   * @return the entity, or null if there is none with that id or no game refers to it
   */
  <T> @Nullable T getEntity(@NotNull EntityKind<T> kind, long id);

  /**
   * Finds the entities of a kind matching a query. Entities no game refers to are never included.
   *
   * @throws IllegalArgumentException if the query uses an unknown filter or sort field, or an
   *     invalid value
   */
  <T> @NotNull ResultPage<T> findEntities(@NotNull EntityKind<T> kind, @NotNull Query query);

  /** The fields entities of a kind can be filtered and sorted on in this database. */
  @NotNull
  SearchSchema entitySearchSchema(@NotNull EntityKind<?> kind);

  /**
   * Updates the descriptive fields of an existing entity. Which games refer to it is unchanged.
   *
   * @return the updated entity
   * @throws UnsupportedOperationException if entities can't be edited
   * @throws IllegalArgumentException if there is no such entity, or the update would make it equal
   *     to another existing entity
   */
  <T> @NotNull T updateEntity(@NotNull EntityKind<T> kind, long id, @NotNull T entity);

  // ── Extensions ───────────────────────────────────────────────────────────

  /**
   * Gets a format-specific extension of this database.
   *
   * @param type the extension interface
   * @return the extension, or empty if this database doesn't offer it
   */
  default <X> @NotNull Optional<X> extension(@NotNull Class<X> type) {
    return type.isInstance(this) ? Optional.of(type.cast(this)) : Optional.empty();
  }

  @Override
  void close() throws IOException;
}

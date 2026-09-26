package se.yarin.morphy.pgn;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Predicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.chess.GameModel;
import se.yarin.chess.pgn.NagStyle;
import se.yarin.chess.pgn.PgnExporter;
import se.yarin.chess.pgn.PgnFormatException;
import se.yarin.chess.pgn.PgnFormatOptions;
import se.yarin.chess.pgn.PgnParser;
import se.yarin.morphy.api.AccessMode;
import se.yarin.morphy.api.Capabilities;
import se.yarin.morphy.api.Database;
import se.yarin.morphy.api.DatabaseFormat;
import se.yarin.morphy.api.EntityKind;
import se.yarin.morphy.api.GameFetchOptions;
import se.yarin.morphy.api.query.FilterCondition;
import se.yarin.morphy.api.query.Query;
import se.yarin.morphy.api.query.QuerySupport;
import se.yarin.morphy.api.query.ResultPage;
import se.yarin.morphy.api.query.SearchSchema;
import se.yarin.morphy.api.query.Sort;
import se.yarin.morphy.model.GameDto;

/**
 * A {@link Database} that is a plain PGN file. The games are numbered by their position in the
 * file, from 1.
 *
 * <p>A PGN file has no entities: a game holds the names of its players and its event itself. The
 * entity DTOs of a game therefore have no ids, and the entity methods of this database find
 * nothing, see {@link Capabilities#hasEntities()}.
 *
 * <p>An index file with the extension {@code .pgi} next to the PGN file, in the format ChessBase
 * uses, makes it quick to find a game in a big file. It is used if it matches the PGN file, and
 * otherwise the PGN file is scanned and, if the database is writable, the index is written again.
 * There is no other search structure: a search looks at every game.
 *
 * <p>Adding a game appends it to the file. Replacing a game writes the whole file anew, with every
 * other game as it was, byte for byte. Nothing is kept in memory but the offsets of the games, so
 * changes made to the file by someone else are not seen until it is opened again. Changes are
 * serialized within this object only, there is no locking against other processes.
 *
 * <p>Games are read as UTF-8, and as Windows-1252 if they aren't valid UTF-8; new games are written
 * as UTF-8. Annotations are the plain PGN comments and NAGs.
 */
public final class DatabasePgn implements Database {
  private static final Logger log = LoggerFactory.getLogger(DatabasePgn.class);

  private final @NotNull Path path;
  private final @NotNull PgnFile file;
  private final boolean writable;
  private final @NotNull PgnGameMapper mapper = new PgnGameMapper(PgnMoves.PLAIN);
  private final @NotNull PgnParser parser = new PgnParser();
  private final @NotNull ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

  private DatabasePgn(@NotNull Path path, @NotNull PgnFile file, boolean writable) {
    this.path = path;
    this.file = file;
    this.writable = writable;
  }

  /**
   * Opens a PGN file.
   *
   * @param mode {@link AccessMode#READ_ONLY} or {@link AccessMode#READ_WRITE}; a read-only database
   *     never writes the index file either
   * @throws UnsupportedOperationException for {@link AccessMode#IN_MEMORY}
   */
  public static @NotNull DatabasePgn open(@NotNull Path path, @NotNull AccessMode mode)
      throws IOException {
    boolean writable =
        switch (mode) {
          case READ_ONLY -> false;
          case READ_WRITE -> true;
          case IN_MEMORY ->
              throw new UnsupportedOperationException("A PGN database can't be opened in memory");
        };
    return new DatabasePgn(path, PgnFile.open(path, writable), writable);
  }

  /**
   * Creates an empty PGN file and opens it for reading and writing.
   *
   * @throws java.nio.file.FileAlreadyExistsException if the file exists
   */
  public static @NotNull DatabasePgn create(@NotNull Path path) throws IOException {
    return new DatabasePgn(path, PgnFile.create(path), true);
  }

  @Override
  public @NotNull String name() {
    String name = path.getFileName().toString();
    int dot = name.lastIndexOf('.');
    return dot <= 0 ? name : name.substring(0, dot);
  }

  @Override
  public @NotNull DatabaseFormat format() {
    return DatabaseFormat.PGN;
  }

  @Override
  public @NotNull Capabilities capabilities() {
    return new Capabilities(writable, false, false);
  }

  // ── Games ────────────────────────────────────────────────────────────────

  @Override
  public long gameCount() {
    lock.readLock().lock();
    try {
      return file.gameCount();
    } finally {
      lock.readLock().unlock();
    }
  }

  @Override
  public @Nullable GameDto getGame(long id, @NotNull GameFetchOptions fetch) {
    lock.readLock().lock();
    try {
      if (id < 1 || id > file.gameCount()) {
        return null;
      }
      return readGame((int) id - 1, fetch.includeMoves());
    } finally {
      lock.readLock().unlock();
    }
  }

  @Override
  public @NotNull ResultPage<GameDto> findGames(
      @NotNull Query query, @NotNull GameFetchOptions fetch) {
    List<FilterCondition> conditions = QuerySupport.conditions(query, PgnGameSearch.DEFAULT_FIELD);
    // Both of these reject unknown fields, so do it before looking at any game
    Predicate<GameDto> filter = PgnGameSearch.filter(conditions);
    Comparator<GameDto> comparator = PgnGameSearch.comparator(query.sort());

    lock.readLock().lock();
    try {
      int total = file.gameCount();
      if (conditions.isEmpty() && QuerySupport.isIdOrder(query.sort())) {
        return listById(query, fetch, total);
      }

      List<GameDto> matches = new ArrayList<>();
      for (int i = 0; i < total; i++) {
        GameDto game = readGameOrNull(i, false);
        if (game != null && filter.test(game)) {
          matches.add(game);
        }
      }
      if (comparator != null) {
        matches.sort(comparator);
      }
      List<GameDto> page = new ArrayList<>();
      for (GameDto match : QuerySupport.slice(matches, query)) {
        page.add(fetch.includeMoves() ? readGame((int) (match.id() - 1), true) : match);
      }
      return new ResultPage<>(
          page, query.offset(), query.limit(), (long) matches.size(), QuerySupport.describe(conditions));
    } finally {
      lock.readLock().unlock();
    }
  }

  /** Lists games straight from the file when the query needs no filtering or sorting. */
  private @NotNull ResultPage<GameDto> listById(
      @NotNull Query query, @NotNull GameFetchOptions fetch, int total) {
    boolean descending =
        !query.sort().isNatural()
            && query.sort().keys().getFirst().direction() == Sort.Direction.DESCENDING;
    List<GameDto> page = new ArrayList<>();
    for (int i = query.offset(); i < query.offset() + query.limit() && i < total; i++) {
      int index = descending ? total - 1 - i : i;
      GameDto game = readGameOrNull(index, fetch.includeMoves());
      if (game != null) {
        page.add(game);
      }
    }
    return new ResultPage<>(page, query.offset(), query.limit(), (long) total, "");
  }

  @Override
  public @NotNull SearchSchema gameSearchSchema() {
    return PgnGameSearch.schema();
  }

  @Override
  public long addGame(@NotNull GameDto game) {
    requireWritable();
    GameModel model = mapper.toModel(game);
    lock.writeLock().lock();
    try {
      String eol = file.lineEnding();
      file.append(export(model, eol), eol);
      return file.gameCount();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } finally {
      lock.writeLock().unlock();
    }
  }

  @Override
  public void replaceGame(long id, @NotNull GameDto game) {
    requireWritable();
    GameModel model = mapper.toModel(game);
    lock.writeLock().lock();
    try {
      if (id < 1 || id > file.gameCount()) {
        throw new IllegalArgumentException("There is no game with id " + id);
      }
      String eol = file.lineEnding();
      file.replace((int) id - 1, export(model, eol), eol);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } finally {
      lock.writeLock().unlock();
    }
  }

  private void requireWritable() {
    if (!writable) {
      throw new UnsupportedOperationException("The database is read-only: " + name());
    }
  }

  /**
   * Writes a game as PGN. A PlyCount tag is written if the game had one, and then from the moves as
   * they are now; a game without one doesn't get one.
   */
  private static @NotNull String export(@NotNull GameModel model, @NotNull String lineEnding) {
    boolean plyCount = model.header().getExtraTag("PlyCount") != null;
    if (plyCount) {
      // The exporter writes its own, from the moves
      model.header().setExtraTag("PlyCount", null);
    }
    PgnFormatOptions options =
        new PgnFormatOptions(79, true, plyCount, true, true, true, NagStyle.NUMERIC, lineEnding);
    return new PgnExporter(options).exportGame(model);
  }

  // ── Reading a game ───────────────────────────────────────────────────────

  private @Nullable GameDto readGameOrNull(int index, boolean includeMoves) {
    try {
      return readGame(index, includeMoves);
    } catch (IllegalStateException e) {
      // One broken game must not make the rest of the file unsearchable
      log.warn("Skipping unreadable game {} of {}: {}", index + 1, path.getFileName(), e.getMessage());
      return null;
    }
  }

  private @NotNull GameDto readGame(int index, boolean includeMoves) {
    try {
      String text = file.gameText(index);
      GameModel model = parser.parseGame(includeMoves ? text : headerOnly(text));
      return mapper.toDto(model, index + 1L, includeMoves);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (PgnFormatException | RuntimeException e) {
      throw new IllegalStateException("Game " + (index + 1) + " can't be read: " + e.getMessage(), e);
    }
  }

  /**
   * The text of a game cut off after its tags, with an empty movetext instead, which is all that is
   * needed to read the header, and much cheaper than reading the moves.
   */
  static @NotNull String headerOnly(@NotNull String game) {
    int pos = 0;
    int n = game.length();
    while (pos < n) {
      int eol = game.indexOf('\n', pos);
      int next = eol < 0 ? n : eol + 1;
      boolean blank = game.substring(pos, next).isBlank();
      if (!blank && game.charAt(pos) != '[') {
        break;
      }
      pos = next;
    }
    return game.substring(0, pos) + "\n*\n";
  }

  // ── Entities: there are none ─────────────────────────────────────────────

  @Override
  public long entityCount(@NotNull EntityKind<?> kind) {
    return 0;
  }

  @Override
  public <T> @Nullable T getEntity(@NotNull EntityKind<T> kind, long id) {
    return null;
  }

  @Override
  public <T> @NotNull ResultPage<T> findEntities(@NotNull EntityKind<T> kind, @NotNull Query query) {
    return new ResultPage<>(List.of(), query.offset(), query.limit(), 0L, "");
  }

  @Override
  public @NotNull SearchSchema entitySearchSchema(@NotNull EntityKind<?> kind) {
    return new SearchSchema("name", List.of(), Set.of(), List.of());
  }

  @Override
  public <T> @NotNull T updateEntity(@NotNull EntityKind<T> kind, long id, @NotNull T entity) {
    throw new UnsupportedOperationException("A PGN database has no entities");
  }

  @Override
  public void close() throws IOException {
    lock.writeLock().lock();
    try {
      file.close();
    } finally {
      lock.writeLock().unlock();
    }
  }
}

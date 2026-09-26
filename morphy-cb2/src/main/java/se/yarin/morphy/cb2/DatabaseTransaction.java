package se.yarin.morphy.cb2;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.cb2.entities.Entity;
import se.yarin.morphy.cb2.entities.EntityType;
import se.yarin.morphy.cb2.games.GameRecord;
import se.yarin.morphy.cb2.indexes.GameListFile;
import se.yarin.morphy.cb2.indexes.SortIndexFile;
import se.yarin.morphy.cb2.storage.RecordFile;
import se.yarin.morphy.chessbase.DatabaseLocks;

/**
 * A transaction on a {@link Database2Cbh}: everything read from a database is read through one.
 * It holds a lock for as long as it's open, so it must be closed, typically with
 * try-with-resources.
 *
 * <p>A {@link ReadTransaction} sees the database as committed. A {@link WriteTransaction} sees
 * its own changes on top of that, and overrides the methods that read the stored records.
 */
public abstract class DatabaseTransaction implements AutoCloseable {
  private final @NotNull Database2Cbh database;
  private final @NotNull DatabaseLocks.Lock lock;
  private boolean closed;

  protected DatabaseTransaction(@NotNull Database2Cbh database, @NotNull DatabaseLocks.Lock lock) {
    database.context().acquire(lock);
    this.database = database;
    this.lock = lock;
  }

  public @NotNull Database2Cbh database() {
    return database;
  }

  /** Whether the transaction has been closed. */
  public boolean isClosed() {
    return closed;
  }

  protected void ensureOpen() {
    if (closed) {
      throw new IllegalStateException("The transaction is closed");
    }
  }

  /** The number of records: games, texts and analyses. */
  public int count() {
    return database.gameHeaderFile().count();
  }

  /**
   * The record of a game, text or analysis.
   *
   * @throws IllegalArgumentException if there is no record with that id
   */
  public @NotNull GameRecord record(int id) {
    ensureOpen();
    if (id < 1 || id > count()) {
      throw new IllegalArgumentException("No game with id " + id + " in " + database.name());
    }
    return database.gameHeaderFile().get(id);
  }

  /**
   * Gets a game, text or analysis.
   *
   * @throws IllegalArgumentException if there is no game with that id
   */
  public @NotNull Game getGame(int id) {
    return new Game(this, record(id));
  }

  /** The stored moves of a record, or the body of a text. */
  protected @NotNull RecordFile.Record movesData(@NotNull GameRecord record) {
    return database.moveFile().read(record.movesOffset());
  }

  /** The stored annotations of a record, or null if it has none. */
  protected byte @Nullable [] annotationData(@NotNull GameRecord record) {
    if (record.annotationOffset() == 0) {
      return null;
    }
    return database.annotationFile().read(record.annotationOffset()).content();
  }

  /** The number of entity ids of a type in use, deleted ones included. */
  public int entityCount(@NotNull EntityType type) {
    return database.entityFile().count(type);
  }

  /**
   * An entity.
   *
   * @return the entity, or null if there is none with that id
   */
  public @Nullable Entity entity(@NotNull EntityType type, long id) {
    ensureOpen();
    if (id < 0 || id >= entityCount(type)) {
      return null;
    }
    return database.entityFile().get(type, (int) id);
  }

  /**
   * An entity of a known type.
   *
   * @return the entity, or null if there is none with that id or it's of another type
   */
  public <T extends Entity> @Nullable T entity(
      @NotNull EntityType type, long id, @NotNull Class<T> clazz) {
    Entity entity = entity(type, id);
    return clazz.isInstance(entity) ? clazz.cast(entity) : null;
  }

  /** The ids of the records referring to an entity in a role, in ascending order. */
  public @NotNull List<Integer> gameIds(long entityId, @NotNull GameListFile.Role role) {
    ensureOpen();
    return database.gameListFile().games((int) entityId, role);
  }

  /** The number of records referring to an entity in a role. */
  public int gameCount(long entityId, @NotNull GameListFile.Role role) {
    ensureOpen();
    return database.gameListFile().count((int) entityId, role);
  }

  /** The ids of the entities of a sort order, in sort order. */
  public @NotNull List<Long> sortedIds(@NotNull SortIndexFile.StandardIndex index) {
    ensureOpen();
    List<Long> ids = new ArrayList<>();
    for (SortIndexFile.Node node : database.sortIndexFile().index(index)) {
      ids.add(node.id());
    }
    return ids;
  }

  /** The number of entities in a sort order. */
  public long sortedCount(@NotNull SortIndexFile.StandardIndex index) {
    ensureOpen();
    return database.sortIndexFile().index(index).count();
  }

  @Override
  public void close() {
    if (!closed) {
      closed = true;
      database.context().release(lock);
    }
  }
}

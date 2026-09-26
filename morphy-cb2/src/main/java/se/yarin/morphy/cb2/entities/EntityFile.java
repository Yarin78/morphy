package se.yarin.morphy.cb2.entities;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.cb2.InvalidDataException;
import se.yarin.morphy.cb2.storage.ByteStore;

/**
 * The {@code .2lid} file of entities.
 *
 * <p>A big-endian header gives, per entity type, its container size, its number of entities and
 * the first of its deleted entities. Then come blocks: block {@code i} holds entity {@code i} of
 * every type, each in a container of its type's size, in the order of {@link EntityType}. A record
 * of length 0 marks an unused container. The file ends right after the last record written.
 *
 * <p>Deleted entities keep their container and stay in the count; they form a linked list per
 * type, newest first, each deleted record holding the id of the next.
 */
public final class EntityFile implements AutoCloseable {

  public static final int HEADER_SIZE = 184;
  private static final int TYPE_ENTRY_SIZE = 20;
  private static final int TYPES_OFFSET = 8;

  /** The bytes every deleted record holds after its length. */
  private static final byte[] DELETED_MARK = {0x22, 0x33, 0x44, 0x55, 0x66, 0x77, (byte) 0x88, (byte) 0x99};
  private static final int DELETED_LENGTH = 16;

  private final @NotNull ByteStore store;
  private final @NotNull String name;
  private final int headerSize;
  private final int[] containerSizes;
  private final int[] typeOffsets;
  private final int blockSize;
  private final long[] counts;
  private final long[] firstDeleted;
  private final Map<EntityType, BitSet> deleted = new EnumMap<>(EntityType.class);

  public EntityFile(@NotNull ByteStore store, @NotNull String name) {
    this.store = store;
    this.name = name;
    if (store.size() < 8) {
      throw new InvalidDataException(name + " is too short to hold a header");
    }
    ByteBuffer start = store.read(0, 8).order(ByteOrder.BIG_ENDIAN);
    this.headerSize = start.getInt();
    int typeCount = start.getInt();
    if (typeCount != EntityType.values().length || headerSize < TYPES_OFFSET + typeCount * 20) {
      throw new InvalidDataException("Unexpected entity file header in " + name);
    }
    ByteBuffer header = store.read(0, headerSize).order(ByteOrder.BIG_ENDIAN);
    containerSizes = new int[typeCount];
    typeOffsets = new int[typeCount];
    counts = new long[typeCount];
    firstDeleted = new long[typeCount];
    int offset = 0;
    for (int i = 0; i < typeCount; i++) {
      int at = TYPES_OFFSET + TYPE_ENTRY_SIZE * i;
      containerSizes[i] = header.getInt(at);
      counts[i] = header.getLong(at + 4);
      firstDeleted[i] = header.getLong(at + 12);
      typeOffsets[i] = offset;
      offset += containerSizes[i];
    }
    blockSize = offset;
    for (EntityType type : EntityType.values()) {
      deleted.put(type, readDeleted(type));
    }
  }

  /** Initialises an empty {@code .2lid} file with the standard container sizes. */
  public static void create(@NotNull ByteStore store) {
    ByteBuffer header = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.BIG_ENDIAN);
    header.putInt(HEADER_SIZE).putInt(EntityType.values().length);
    for (EntityType type : EntityType.values()) {
      header.putInt(type.standardContainerSize()).putLong(0).putLong(-1);
    }
    // Pairs of unknown meaning: (-1, 1), then (i, 1) for every entity type
    for (int i = -1; i < EntityType.values().length; i++) {
      header.putInt(i).putInt(1);
    }
    store.setSize(0);
    store.write(0, header.flip());
  }

  public @NotNull ByteStore store() {
    return store;
  }

  /** The container size of a type. */
  public int containerSize(@NotNull EntityType type) {
    return containerSizes[type.index()];
  }

  /** The number of entity ids of a type in use, deleted ones included. */
  public int count(@NotNull EntityType type) {
    return (int) counts[type.index()];
  }

  /** The id of the most recently deleted entity of a type, -1 if none. */
  public long firstDeleted(@NotNull EntityType type) {
    return firstDeleted[type.index()];
  }

  /**
   * The ids new entities of a type will get, in order, as far as the list of deleted entities
   * reaches: that list's ids, most recent first. After them come {@link #count} and up.
   */
  public @NotNull List<Integer> freeIds(@NotNull EntityType type) {
    List<Integer> free = new ArrayList<>();
    long id = firstDeleted(type);
    while (id >= 0) {
      free.add((int) id);
      id = nextDeleted(type, (int) id);
    }
    return free;
  }

  /** Whether an entity is deleted. */
  public boolean isDeleted(@NotNull EntityType type, int id) {
    return deleted.get(type).get(id);
  }

  private long containerOffset(EntityType type, int id) {
    return headerSize + (long) id * blockSize + typeOffsets[type.index()];
  }

  /** The raw container of an entity, zero-padded where the file ends early. */
  public byte @NotNull [] readContainer(@NotNull EntityType type, int id) {
    int size = containerSize(type);
    byte[] bytes = new byte[size];
    long offset = containerOffset(type, id);
    long available = Math.min(size, store.size() - offset);
    if (available > 0) {
      store.read(offset, (int) available).get(bytes, 0, (int) available);
    }
    return bytes;
  }

  /** The raw record of an entity, its leading length included; empty if the container is unused. */
  public byte @NotNull [] readRecord(@NotNull EntityType type, int id) {
    byte[] container = readContainer(type, id);
    int length = ByteBuffer.wrap(container).order(ByteOrder.LITTLE_ENDIAN).getInt();
    if (length < 0 || length + 4 > container.length) {
      throw new InvalidDataException(
          "The " + type + " record " + id + " doesn't fit its container in " + name);
    }
    byte[] record = new byte[length + 4];
    System.arraycopy(container, 0, record, 0, record.length);
    return record;
  }

  /**
   * Reads an entity.
   *
   * @return the entity, or null if there is none with that id or it is deleted
   * @throws InvalidDataException if the record can't be decoded
   */
  public @Nullable Entity get(@NotNull EntityType type, int id) {
    if (id < 0 || id >= count(type) || isDeleted(type, id)) {
      return null;
    }
    byte[] record = readRecord(type, id);
    if (record.length == 4 || isDeletedRecord(record)) {
      return null;
    }
    try {
      return Entity.decode(type, record);
    } catch (InvalidDataException e) {
      throw new InvalidDataException(type + " " + id + " in " + name + ": " + e.getMessage(), e);
    }
  }

  /**
   * Writes an entity to an existing id.
   *
   * @throws IllegalArgumentException if the record doesn't fit the container
   */
  public void put(int id, @NotNull Entity entity) {
    EntityType type = entity.type();
    if (id < 0 || id >= count(type)) {
      throw new IllegalArgumentException("No " + type + " " + id);
    }
    deleted.get(type).clear(id);
    writeRecord(type, id, entity.encode());
  }

  /**
   * Adds an entity, reusing the most recently deleted id of its type if there is one.
   *
   * @return the id of the new entity
   */
  public int add(@NotNull Entity entity) {
    EntityType type = entity.type();
    int t = type.index();
    int id;
    if (firstDeleted[t] >= 0) {
      id = (int) firstDeleted[t];
      firstDeleted[t] = nextDeleted(type, id);
    } else {
      id = (int) counts[t];
      counts[t]++;
    }
    writeHeaderEntry(type);
    deleted.get(type).clear(id);
    writeRecord(type, id, entity.encode());
    return id;
  }

  /** Deletes an entity, putting it first in its type's list of deleted entities. */
  public void delete(@NotNull EntityType type, int id) {
    if (id < 0 || id >= count(type) || isDeleted(type, id)) {
      throw new IllegalArgumentException("No " + type + " " + id + " to delete");
    }
    ByteBuffer record = ByteBuffer.allocate(4 + DELETED_LENGTH).order(ByteOrder.LITTLE_ENDIAN);
    record.putInt(DELETED_LENGTH).put(DELETED_MARK).putLong(firstDeleted[type.index()]);
    writeRecord(type, id, record.array());
    firstDeleted[type.index()] = id;
    deleted.get(type).set(id);
    writeHeaderEntry(type);
  }

  private void writeRecord(EntityType type, int id, byte[] record) {
    if (record.length > containerSize(type)) {
      throw new IllegalArgumentException(
          "The " + type + " record is " + record.length + " bytes, more than its container");
    }
    long offset = containerOffset(type, id);
    // Grow the file with zeros up to the container if it ends before it
    if (store.size() < offset) {
      store.setSize(offset);
    }
    // Zero what's left of a longer record that was there before
    int old = 0;
    if (store.size() >= offset + 4) {
      old = Math.max(0, store.read(offset, 4).getInt() + 4);
      old = (int) Math.min(old, Math.min(containerSize(type), store.size() - offset));
    }
    byte[] bytes = record;
    if (old > record.length) {
      bytes = new byte[old];
      System.arraycopy(record, 0, bytes, 0, record.length);
    }
    store.write(offset, ByteBuffer.wrap(bytes));
  }

  /**
   * Whether a record is a deleted record. A deleted record is recognised by its mark as well as
   * by the list, since deleted records the list doesn't reach have been seen.
   */
  private static boolean isDeletedRecord(byte[] record) {
    if (record.length != 4 + DELETED_LENGTH) {
      return false;
    }
    for (int i = 0; i < DELETED_MARK.length; i++) {
      if (record[4 + i] != DELETED_MARK[i]) {
        return false;
      }
    }
    return true;
  }

  private long nextDeleted(EntityType type, int id) {
    ByteBuffer container = ByteBuffer.wrap(readContainer(type, id)).order(ByteOrder.LITTLE_ENDIAN);
    if (container.getInt(0) != DELETED_LENGTH) {
      throw new InvalidDataException(
          "The deleted " + type + " " + id + " doesn't hold a deleted record in " + name);
    }
    return container.getLong(4 + DELETED_MARK.length);
  }

  private BitSet readDeleted(EntityType type) {
    BitSet set = new BitSet();
    long id = firstDeleted[type.index()];
    while (id >= 0) {
      if (id >= count(type) || set.get((int) id)) {
        throw new InvalidDataException("The list of deleted " + type + " entities is broken");
      }
      set.set((int) id);
      id = nextDeleted(type, (int) id);
    }
    return set;
  }

  private void writeHeaderEntry(EntityType type) {
    int t = type.index();
    ByteBuffer entry = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN);
    entry.putLong(counts[t]).putLong(firstDeleted[t]).flip();
    store.write(TYPES_OFFSET + (long) TYPE_ENTRY_SIZE * t + 4, entry);
  }

  @Override
  public void close() {
    store.close();
  }
}

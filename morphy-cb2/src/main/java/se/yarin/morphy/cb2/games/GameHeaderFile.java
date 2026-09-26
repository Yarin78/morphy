package se.yarin.morphy.cb2.games;

import java.nio.ByteBuffer;
import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.cb2.InvalidDataException;
import se.yarin.morphy.cb2.storage.ByteStore;

/**
 * The {@code .2cbh} file: a 192-byte header followed by one 192-byte {@link GameRecord} per game,
 * in game id order and with no gaps. The record of game {@code n} starts at {@code 192·n}.
 */
public final class GameHeaderFile implements AutoCloseable {
  public static final int HEADER_SIZE = 192;
  public static final int VERSION = 5;

  private static final int OFFSET_UNKNOWN_38 = 0x08;
  private static final int OFFSET_RECORD_SIZE = 0x0a;
  private static final int OFFSET_VERSION = 0x0d;
  private static final int OFFSET_NEXT_ID = 0x10;

  private final @NotNull ByteStore store;
  private final @NotNull String name;

  public GameHeaderFile(@NotNull ByteStore store, @NotNull String name) {
    this.store = store;
    this.name = name;
    if (store.size() < HEADER_SIZE) {
      throw new InvalidDataException(name + " is too short to hold a header");
    }
    ByteBuffer header = store.read(0, HEADER_SIZE);
    int recordSize = header.getShort(OFFSET_RECORD_SIZE);
    if (recordSize != GameRecord.SIZE) {
      throw new InvalidDataException("Unexpected record size " + recordSize + " in " + name);
    }
    if ((store.size() - HEADER_SIZE) % GameRecord.SIZE != 0) {
      throw new InvalidDataException(name + " doesn't hold a whole number of records");
    }
  }

  /** Initialises an empty {@code .2cbh} file. */
  public static void create(@NotNull ByteStore store) {
    ByteBuffer header = ByteStore.allocate(HEADER_SIZE);
    header.putShort(OFFSET_UNKNOWN_38, (short) 38);
    header.putShort(OFFSET_RECORD_SIZE, (short) GameRecord.SIZE);
    header.put(OFFSET_VERSION, (byte) VERSION);
    header.putInt(OFFSET_NEXT_ID, 1);
    store.setSize(0);
    store.write(0, header);
  }

  public @NotNull ByteStore store() {
    return store;
  }

  /** The format version in the header. */
  public int version() {
    return store.read(OFFSET_VERSION, 1).get() & 0xFF;
  }

  /** The number of records, deleted ones included. */
  public int count() {
    return (int) ((store.size() - HEADER_SIZE) / GameRecord.SIZE);
  }

  /** The id of the next game to be added, as stored in the header. */
  public int nextGameId() {
    return store.read(OFFSET_NEXT_ID, 4).getInt();
  }

  private void setNextGameId(int id) {
    store.write(OFFSET_NEXT_ID, ByteStore.allocate(4).putInt(id).flip());
  }

  /** The raw bytes of a record. */
  public byte @NotNull [] readRaw(int id) {
    checkId(id);
    byte[] bytes = new byte[GameRecord.SIZE];
    store.read((long) id * GameRecord.SIZE, GameRecord.SIZE).get(bytes);
    return bytes;
  }

  /** Reads a record. */
  public @NotNull GameRecord get(int id) {
    return GameRecord.decode(id, readRaw(id));
  }

  /** Overwrites an existing record. */
  public void put(@NotNull GameRecord record) {
    checkId(record.id());
    store.write((long) record.id() * GameRecord.SIZE, ByteBuffer.wrap(record.encode()));
  }

  /**
   * Appends a record, which must have the id following the last one.
   *
   * @throws IllegalArgumentException if the id is not the next one
   */
  public void append(@NotNull GameRecord record) {
    if (record.id() != count() + 1) {
      throw new IllegalArgumentException(
          "Can't append game " + record.id() + " after " + count() + " games");
    }
    store.write((long) record.id() * GameRecord.SIZE, ByteBuffer.wrap(record.encode()));
    setNextGameId(record.id() + 1);
  }

  private void checkId(int id) {
    if (id < 1 || id > count()) {
      throw new IllegalArgumentException("No game " + id + " in " + name);
    }
  }

  @Override
  public void close() {
    store.close();
  }
}

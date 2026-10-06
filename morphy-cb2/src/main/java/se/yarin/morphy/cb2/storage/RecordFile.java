package se.yarin.morphy.cb2.storage;

import java.nio.ByteBuffer;
import java.util.Arrays;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.morphy.cb2.InvalidDataException;

/**
 * A file of framed records: the {@code .2cbg} file of moves and texts, and the {@code .2cba} file
 * of annotations. A 12-byte header is followed by records back to back, each found through an
 * offset kept in the {@code .2cbh} file.
 *
 * <p>A record is a magic number, the size of its content (A) and of its spare area (B), a
 * checksum of the content, a two-byte tag, the content, B zero bytes, and finally the record
 * length {@code A + B + 34}.
 */
public final class RecordFile implements AutoCloseable {
  private static final Logger log = LoggerFactory.getLogger(RecordFile.class);

  public static final int HEADER_SIZE = 12;
  /** The bytes of a record that aren't content or spare. */
  public static final int FRAMING_SIZE = 34;
  /** The offset of the content within a record. */
  public static final int CONTENT_OFFSET = 26;

  // Records further apart than this aren't read as one span; also how much is read after the last
  // record of a span, to take it in
  private static final int MAX_SPAN_GAP = 64 * 1024;
  // The longest span read at once
  private static final int MAX_SPAN = 16 * 1024 * 1024;

  private static final byte[] MAGIC = {
    (byte) 0x88, 0x77, 0x66, 0x55, 0x44, 0x33, 0x22, 0x11
  };

  /** The tag of a game in normal chess, and of an analysis. */
  public static final int TAG_GAME = 0x0001;
  /** The tag of a Chess960 game. */
  public static final int TAG_CHESS960 = 0x0002;
  /** The tag of the body of a guiding text. */
  public static final int TAG_TEXT = 0x1000;
  /** The tag of the annotations of a game. */
  public static final int TAG_ANNOTATIONS = 0x2000;

  /**
   * One record.
   *
   * @param tag what the record holds, one of the {@code TAG_} constants
   * @param content the content
   * @param spare the size of the spare area after the content
   */
  public record Record(int tag, byte @NotNull [] content, int spare) {
    /** The number of bytes the record takes in the file. */
    public int length() {
      return content.length + spare + FRAMING_SIZE;
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof Record r
          && tag == r.tag
          && spare == r.spare
          && Arrays.equals(content, r.content);
    }

    @Override
    public int hashCode() {
      return 31 * Arrays.hashCode(content) + tag * 7 + spare;
    }
  }

  private final @NotNull ByteStore store;
  private final @NotNull String name;

  /**
   * Opens a record file.
   *
   * @param store the bytes of the file
   * @param name a name for messages, e.g. the file name
   */
  public RecordFile(@NotNull ByteStore store, @NotNull String name) {
    if (store.size() < HEADER_SIZE) {
      throw new InvalidDataException(name + " is too short to hold a header");
    }
    this.store = store;
    this.name = name;
    int headerSize = store.read(8, 2).getShort();
    if (headerSize != HEADER_SIZE) {
      throw new InvalidDataException("Unexpected header size " + headerSize + " in " + name);
    }
  }

  /**
   * Initialises an empty record file.
   *
   * @param store an empty store
   * @param unknownByte the byte at 0x0a
   * @param version the byte at 0x0b
   */
  public static void create(@NotNull ByteStore store, int unknownByte, int version) {
    ByteBuffer header = ByteStore.allocate(HEADER_SIZE);
    header.putLong(HEADER_SIZE).putShort((short) HEADER_SIZE);
    header.put((byte) unknownByte).put((byte) version);
    store.setSize(0);
    store.write(0, header.flip());
  }

  public @NotNull ByteStore store() {
    return store;
  }

  /** The size of the file, where a new record would be appended. */
  public long size() {
    return store.size();
  }

  /** The format version byte at 0x0b of the header. */
  public int version() {
    return store.read(11, 1).get() & 0xFF;
  }

  /** The byte at 0x0a of the header, whose meaning is unknown. */
  public int unknownHeaderByte() {
    return store.read(10, 1).get() & 0xFF;
  }

  /** Sets the two header bytes at 0x0a and 0x0b. */
  public void setHeaderBytes(int unknownByte, int version) {
    store.write(10, ByteBuffer.wrap(new byte[] {(byte) unknownByte, (byte) version}));
  }

  /**
   * The length in bytes of the record at an offset, framing included.
   *
   * @throws InvalidDataException if there is no record at the offset
   */
  public int recordLength(long offset) {
    ByteBuffer head = readHead(offset);
    return head.getInt(8) + head.getInt(12) + FRAMING_SIZE;
  }

  /**
   * Reads a record.
   *
   * @param offset where the record begins
   * @return the record
   * @throws InvalidDataException if there is no record at the offset
   */
  public @NotNull Record read(long offset) {
    ByteBuffer head = readHead(offset);
    int contentSize = head.getInt(8);
    int spare = head.getInt(12);
    if (contentSize < 0 || spare < 0 || offset + contentSize + spare + FRAMING_SIZE > size()) {
      throw new InvalidDataException(
          String.format("Record at offset %d in %s doesn't fit in the file", offset, name));
    }
    return parse(store.read(offset, CONTENT_OFFSET + contentSize), 0, offset);
  }

  /**
   * Reads the records at some offsets with few reads: offsets close together, as those of games
   * in id order are, are read as one span, and the records cut out of it.
   *
   * @param offsets where the records begin
   * @return the records, in the order of the offsets
   * @throws InvalidDataException if there is no record at one of the offsets
   */
  public @NotNull Record @NotNull [] readMany(long @NotNull [] offsets) {
    Record[] records = new Record[offsets.length];
    Integer[] order = new Integer[offsets.length];
    for (int i = 0; i < order.length; i++) {
      order[i] = i;
    }
    Arrays.sort(order, (a, b) -> Long.compare(offsets[a], offsets[b]));
    int i = 0;
    while (i < order.length) {
      long start = offsets[order[i]];
      int j = i + 1;
      while (j < order.length
          && offsets[order[j]] - offsets[order[j - 1]] <= MAX_SPAN_GAP
          && offsets[order[j]] - start <= MAX_SPAN) {
        j++;
      }
      // Up to the last record's start, and as much after it as a record usually takes
      long end = Math.min(size(), offsets[order[j - 1]] + MAX_SPAN_GAP);
      ByteBuffer span = start >= HEADER_SIZE && end > start ? store.read(start, (int) (end - start)) : null;
      for (int k = i; k < j; k++) {
        int index = order[k];
        long offset = offsets[index];
        int at = (int) (offset - start);
        Record record = span == null ? null : parseIfInside(span, at, offset);
        // A record reaching past the span is read by itself
        records[index] = record != null ? record : read(offset);
      }
      i = j;
    }
    return records;
  }

  /** The record at a position of a buffer, or null if its content doesn't fit in it. */
  private Record parseIfInside(ByteBuffer buf, int at, long offset) {
    if (at + CONTENT_OFFSET > buf.limit()) {
      return null;
    }
    int contentSize = buf.getInt(at + 8);
    if (contentSize < 0 || at + CONTENT_OFFSET + (long) contentSize > buf.limit()) {
      return null;
    }
    return parse(buf, at, offset);
  }

  /**
   * The record at a position of a buffer holding at least its head and content: its magic number
   * checked, and its checksum (a wrong one is only logged).
   */
  private Record parse(ByteBuffer buf, int at, long offset) {
    for (int i = 0; i < MAGIC.length; i++) {
      if (buf.get(at + i) != MAGIC[i]) {
        throw new InvalidDataException("No record at offset " + offset + " in " + name);
      }
    }
    int contentSize = buf.getInt(at + 8);
    int spare = buf.getInt(at + 12);
    if (spare < 0 || offset + contentSize + spare + FRAMING_SIZE > size()) {
      throw new InvalidDataException(
          String.format("Record at offset %d in %s doesn't fit in the file", offset, name));
    }
    byte[] storedChecksum = new byte[8];
    buf.get(at + 16, storedChecksum);
    int tag = buf.getShort(at + 24) & 0xFFFF;
    byte[] content = new byte[contentSize];
    buf.get(at + CONTENT_OFFSET, content);
    if (!Arrays.equals(storedChecksum, checksum(content))) {
      log.warn("Wrong checksum in the record at offset {} in {}", offset, name);
    }
    return new Record(tag, content, spare);
  }

  private ByteBuffer readHead(long offset) {
    if (offset < HEADER_SIZE || offset + CONTENT_OFFSET > size()) {
      throw new InvalidDataException("No record at offset " + offset + " in " + name);
    }
    ByteBuffer head = store.read(offset, CONTENT_OFFSET);
    for (int i = 0; i < MAGIC.length; i++) {
      if (head.get(i) != MAGIC[i]) {
        throw new InvalidDataException("No record at offset " + offset + " in " + name);
      }
    }
    return head;
  }

  /**
   * Writes a record at an offset, overwriting what's there. The caller is responsible for not
   * overwriting other records.
   */
  public void write(long offset, @NotNull Record record) {
    store.write(offset, frame(record));
    updateSizeField();
  }

  /**
   * Appends a record at the end of the file.
   *
   * @return the offset of the record
   */
  public long append(@NotNull Record record) {
    long offset = size();
    write(offset, record);
    return offset;
  }

  /**
   * Moves bytes within the file, as when records are shifted to make room. The ranges may
   * overlap.
   *
   * @param from where the bytes are
   * @param to where they go
   * @param length how many bytes
   */
  public void move(long from, long to, long length) {
    final int chunk = 1 << 20;
    if (to > from) {
      for (long done = 0; done < length; ) {
        int n = (int) Math.min(chunk, length - done);
        long at = from + length - done - n;
        store.write(at + (to - from), store.read(at, n));
        done += n;
      }
    } else if (to < from) {
      for (long done = 0; done < length; ) {
        int n = (int) Math.min(chunk, length - done);
        store.write(to + done, store.read(from + done, n));
        done += n;
      }
    }
  }

  /** Sets the size of the file, as after the last record has shrunk. */
  public void setSize(long size) {
    store.setSize(size);
    updateSizeField();
  }

  private void updateSizeField() {
    ByteBuffer buf = ByteStore.allocate(8).putLong(store.size()).flip();
    store.write(0, buf);
  }

  /** A record with its framing, as it's stored. */
  public static @NotNull ByteBuffer frame(@NotNull Record record) {
    ByteBuffer buf = ByteStore.allocate(record.length());
    buf.put(MAGIC);
    buf.putInt(record.content().length);
    buf.putInt(record.spare());
    buf.put(checksum(record.content()));
    buf.putShort((short) record.tag());
    buf.put(record.content());
    buf.position(buf.position() + record.spare());
    buf.putLong(record.length());
    return buf.flip();
  }

  /**
   * The checksum of a record's content, as stored. The first {@code 8·m} bytes, {@code m =
   * length / 8}, are split into 8 runs of {@code m} bytes; byte {@code i} of the value, from the
   * least significant, is the sum of run {@code i} modulo 256; and the value is stored big-endian.
   * With fewer than 8 bytes the value is the content itself, zero-padded.
   */
  public static byte @NotNull [] checksum(byte @NotNull [] content) {
    byte[] sums = new byte[8];
    int m = content.length / 8;
    if (m == 0) {
      System.arraycopy(content, 0, sums, 0, content.length);
    } else {
      for (int i = 0; i < 8; i++) {
        int sum = 0;
        for (int j = i * m; j < (i + 1) * m; j++) {
          sum += content[j];
        }
        sums[i] = (byte) sum;
      }
    }
    byte[] stored = new byte[8];
    for (int i = 0; i < 8; i++) {
      stored[i] = sums[7 - i];
    }
    return stored;
  }

  @Override
  public void close() {
    store.close();
  }
}

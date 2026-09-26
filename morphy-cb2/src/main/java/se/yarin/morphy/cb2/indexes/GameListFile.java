package se.yarin.morphy.cb2.indexes;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.cb2.InvalidDataException;
import se.yarin.morphy.cb2.storage.ByteStore;

/**
 * The {@code .2lgd} file: for every entity and every role it can play, the records that refer to
 * it. See format/v2/5-indexes.md.
 *
 * <p>A 12-byte header is followed by 512-byte records. The first half of record {@code i} holds
 * the list heads of every entity with id {@code i}, of whatever type: for each {@link Role}, the
 * first and last entry and the number of games. The second half is list block {@code i} of a pool
 * shared by the lists too long to fit in a head: the id of the next block, a count, and up to 30
 * game ids.
 */
public final class GameListFile implements AutoCloseable {

  public static final int HEADER_SIZE = 12;
  public static final int RECORD_SIZE = 512;
  private static final int HALF_SIZE = 256;
  public static final int IDS_PER_BLOCK = 30;

  static final long SINGLE = 0x4000000000000000L;
  static final long RANGE = 0x2000000000000000L;
  private static final long VALUE_MASK = 0x0FFFFFFFFFFFFFFFL;

  private static final int SLOT_LAST = 10, SLOT_COUNT = 20, SLOT_UNKNOWN = 30;

  /**
   * The roles an entity plays for the records referring to it; the number is the role's slot.
   */
  public enum Role {
    PLAYER(1),
    TOURNAMENT(2),
    SOURCE(3),
    ANNOTATOR(4),
    TEAM(5),
    GAME_TAG(6),
    TEXT_TITLE(7),
    ANALYSIS_TITLE(8);

    private final int slot;

    Role(int slot) {
      this.slot = slot;
    }

    public int slot() {
      return slot;
    }
  }

  /** The form a list is stored in. */
  public enum Form {
    EMPTY,
    SINGLE,
    RANGE,
    CHAIN
  }

  private final @NotNull ByteStore store;
  private final @NotNull String name;
  private int blocksInUse;

  public GameListFile(@NotNull ByteStore store, @NotNull String name) {
    this.store = store;
    this.name = name;
    if (store.size() < HEADER_SIZE || (store.size() - HEADER_SIZE) % RECORD_SIZE != 0) {
      throw new InvalidDataException(name + " doesn't hold a header and whole records");
    }
    ByteBuffer header = store.read(0, HEADER_SIZE);
    if (header.getInt(0) != HALF_SIZE) {
      throw new InvalidDataException("Unexpected header in " + name);
    }
    this.blocksInUse = header.getInt(4);
  }

  /** Initialises an empty {@code .2lgd} file. */
  public static void create(@NotNull ByteStore store) {
    ByteBuffer header = ByteStore.allocate(HEADER_SIZE);
    header.putInt(HALF_SIZE).putInt(0).putInt(0).flip();
    store.setSize(0);
    store.write(0, header);
  }

  public @NotNull ByteStore store() {
    return store;
  }

  /** The number of 512-byte records in the file. */
  public int recordCount() {
    return (int) ((store.size() - HEADER_SIZE) / RECORD_SIZE);
  }

  /** The number of list blocks in use, according to the header. */
  public int blocksInUse() {
    return blocksInUse;
  }

  private long recordOffset(int record) {
    return HEADER_SIZE + (long) record * RECORD_SIZE;
  }

  private long headSlot(int entityId, int slot) {
    if (entityId >= recordCount()) {
      return slot == SLOT_UNKNOWN ? 12 : slot >= SLOT_COUNT ? 0 : -1;
    }
    return store.read(recordOffset(entityId) + 8L * slot, 8).getLong();
  }

  /** The number of games in a list, as stored in its head. */
  public int count(int entityId, @NotNull Role role) {
    return (int) headSlot(entityId, SLOT_COUNT + role.slot());
  }

  /** The form a list is stored in. */
  public @NotNull Form form(int entityId, @NotNull Role role) {
    long first = headSlot(entityId, role.slot());
    if (first == -1) {
      return Form.EMPTY;
    }
    if ((first & SINGLE) != 0) {
      return Form.SINGLE;
    }
    return (first & RANGE) != 0 ? Form.RANGE : Form.CHAIN;
  }

  /**
   * The games of a list, in ascending order. A game referring to an entity twice in the same role
   * is listed twice.
   */
  public @NotNull List<Integer> games(int entityId, @NotNull Role role) {
    List<Integer> games = new ArrayList<>();
    long first = headSlot(entityId, role.slot());
    long last = headSlot(entityId, SLOT_LAST + role.slot());
    if (first == -1) {
      return games;
    }
    if ((first & SINGLE) != 0) {
      games.add((int) (first & VALUE_MASK));
      if (last != -1) {
        games.add((int) last);
      }
    } else if ((first & RANGE) != 0) {
      for (long id = first & VALUE_MASK; id <= (last & VALUE_MASK); id++) {
        games.add((int) id);
      }
    } else {
      int block = (int) first;
      int steps = 0;
      while (block != -1) {
        if (block < 0 || block >= recordCount() || ++steps > recordCount()) {
          throw new InvalidDataException("A broken chain of list blocks in " + name);
        }
        ByteBuffer buf = readBlock(block);
        int count = (int) buf.getLong(8);
        for (int i = 0; i < count; i++) {
          games.add((int) buf.getLong(16 + 8 * i));
        }
        block = (int) buf.getLong(0);
      }
    }
    return games;
  }

  /** The blocks of a list stored as a chain, in order; empty for any other form. */
  public @NotNull List<Integer> chain(int entityId, @NotNull Role role) {
    List<Integer> blocks = new ArrayList<>();
    if (form(entityId, role) != Form.CHAIN) {
      return blocks;
    }
    int block = (int) headSlot(entityId, role.slot());
    while (block != -1) {
      blocks.add(block);
      block = (int) readBlock(block).getLong(0);
    }
    return blocks;
  }

  private ByteBuffer readBlock(int block) {
    return store.read(recordOffset(block) + HALF_SIZE, HALF_SIZE);
  }

  /**
   * Replaces a list.
   *
   * <p>Up to two games are stored in the head, and consecutive games as a range. Longer lists are
   * a chain of blocks, reusing the blocks the list had before; blocks it no longer needs are left
   * abandoned, with a next of 0, as ChessBase does.
   *
   * @param entityId the entity
   * @param role the role
   * @param games the games, in ascending order
   */
  public void setGames(int entityId, @NotNull Role role, @NotNull List<Integer> games) {
    List<Integer> oldBlocks = chain(entityId, role);
    ensureRecords(entityId + 1);
    long first, last;
    int n = games.size();
    if (n == 0) {
      first = -1;
      last = -1;
    } else if (n <= 2) {
      first = SINGLE | games.get(0);
      last = n == 2 ? games.get(1) : -1;
    } else if (isRange(games)) {
      first = RANGE | games.getFirst();
      last = RANGE | games.getLast();
    } else {
      int needed = (n + IDS_PER_BLOCK - 1) / IDS_PER_BLOCK;
      List<Integer> blocks = new ArrayList<>(oldBlocks.subList(0, Math.min(needed, oldBlocks.size())));
      while (blocks.size() < needed) {
        blocks.add(allocateBlock());
      }
      for (int b = 0; b < needed; b++) {
        ByteBuffer buf = ByteStore.allocate(HALF_SIZE);
        int from = b * IDS_PER_BLOCK, to = Math.min(n, from + IDS_PER_BLOCK);
        buf.putLong(b + 1 < needed ? blocks.get(b + 1) : -1);
        buf.putLong(to - from);
        for (int i = 0; i < IDS_PER_BLOCK; i++) {
          buf.putLong(from + i < to ? games.get(from + i) : -1);
        }
        store.write(recordOffset(blocks.get(b)) + HALF_SIZE, buf.flip());
      }
      // Only the blocks beyond those reused are left over
      oldBlocks = oldBlocks.subList(Math.min(needed, oldBlocks.size()), oldBlocks.size());
      first = blocks.getFirst();
      last = blocks.getLast();
    }
    for (int block : oldBlocks) {
      store.write(recordOffset(block) + HALF_SIZE, ByteStore.allocate(8).putLong(0).flip());
    }
    ByteBuffer slot = ByteStore.allocate(8);
    store.write(recordOffset(entityId) + 8L * role.slot(), slot.putLong(0, first));
    store.write(recordOffset(entityId) + 8L * (SLOT_LAST + role.slot()), slot.putLong(0, last));
    store.write(recordOffset(entityId) + 8L * (SLOT_COUNT + role.slot()), slot.putLong(0, n));
  }

  private static boolean isRange(List<Integer> games) {
    for (int i = 1; i < games.size(); i++) {
      if (games.get(i) != games.get(i - 1) + 1) {
        return false;
      }
    }
    return true;
  }

  private int allocateBlock() {
    int block = blocksInUse++;
    ensureRecords(block + 1);
    store.write(4, ByteStore.allocate(4).putInt(blocksInUse).flip());
    return block;
  }

  /** Grows the file to hold at least this many records, with empty heads and unused blocks. */
  private void ensureRecords(int records) {
    int current = recordCount();
    if (current >= records) {
      return;
    }
    ByteBuffer empty = ByteStore.allocate(RECORD_SIZE);
    for (int slot = 0; slot < 64; slot++) {
      long value =
          slot < SLOT_COUNT ? -1 : slot < SLOT_UNKNOWN ? 0 : slot == SLOT_UNKNOWN ? 12 : slot == 31 ? 0 : -1;
      if (slot == 33) {
        value = 0; // the count of an unused block
      }
      empty.putLong(value);
    }
    byte[] bytes = empty.array();
    for (int r = current; r < records; r++) {
      store.write(recordOffset(r), ByteBuffer.wrap(bytes));
    }
  }

  @Override
  public void close() {
    store.close();
  }
}

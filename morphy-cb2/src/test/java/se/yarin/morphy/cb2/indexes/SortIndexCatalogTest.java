package se.yarin.morphy.cb2.indexes;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import se.yarin.morphy.cb2.entities.Entity;
import se.yarin.morphy.cb2.entities.GameTag;
import se.yarin.morphy.cb2.indexes.SortIndexFile.StandardIndex;
import se.yarin.morphy.cb2.storage.ByteStore;
import se.yarin.morphy.cb2.storage.MemoryByteStore;

/**
 * The catalog of the {@code .2lcd} file. Newer versions of ChessBase put an index of their own, for
 * time controls, between the indexes of game tags and of the titles of guiding texts, which is
 * where the indexes that follow it move to.
 */
class SortIndexCatalogTest {
  private static final int PAGE = SortIndexFile.PAGE_SIZE;
  private static final int ENTRY = 128;
  private static final int NEW_INDEX_SLOT = 6;

  /** A file with the eight standard indexes, and then the index of the newer versions. */
  private static MemoryByteStore withTimeControlIndex() {
    MemoryByteStore store = new MemoryByteStore();
    SortIndexFile.create(store);
    byte[] catalog = new byte[PAGE];
    store.read(PAGE, PAGE).get(catalog);
    // The entries from the new one on move one slot down
    System.arraycopy(
        catalog,
        NEW_INDEX_SLOT * ENTRY,
        catalog,
        (NEW_INDEX_SLOT + 1) * ENTRY,
        (8 - NEW_INDEX_SLOT) * ENTRY);
    ByteBuffer entry = ByteBuffer.wrap(catalog).order(ByteOrder.BIG_ENDIAN);
    int at = NEW_INDEX_SLOT * ENTRY;
    java.util.Arrays.fill(catalog, at, at + ENTRY, (byte) 0);
    entry.putShort(at, (short) 0);
    entry.putInt(at + 2, 10); // the page after the ones of the eight indexes
    entry.putLong(at + 6, -1);
    entry.putLong(at + 0x0e, 0);
    entry.put(at + 0x16, (byte) 9); // an entity type that doesn't exist here
    entry.put(at + 0x17, (byte) 3);
    entry.put(at + 0x18, (byte) 9);
    entry.put(at + 0x19, (byte) 1);
    byte[] name = "Default Zeitkontrolle".getBytes(StandardCharsets.ISO_8859_1);
    entry.putShort(at + 0x1a, (short) name.length);
    System.arraycopy(name, 0, catalog, at + 0x1c, name.length);
    store.write(PAGE, ByteBuffer.wrap(catalog));
    store.setSize(11L * PAGE);
    store.write(4, ByteStore.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(11).flip());
    return store;
  }

  private static byte[] catalogEntry(MemoryByteStore store, int slot) {
    byte[] bytes = new byte[ENTRY];
    store.read((long) PAGE + slot * ENTRY, ENTRY).get(bytes);
    return bytes;
  }

  @Test
  void anIndexOfAnUnknownTypeIsSetAside() {
    SortIndexFile file = new SortIndexFile(withTimeControlIndex(), "test");
    assertEquals(8, file.indexes().size());
    assertEquals(1, file.unknownIndexes().size());
    SortIndexFile.UnknownIndex unknown = file.unknownIndexes().getFirst();
    assertEquals(NEW_INDEX_SLOT, unknown.catalogSlot());
    assertEquals("Default Zeitkontrolle", unknown.name());
    assertEquals(9, unknown.typeCode());
    assertEquals(0, unknown.count());
  }

  @Test
  void theStandardIndexesAreFoundWhereverTheyAre() {
    SortIndexFile file = new SortIndexFile(withTimeControlIndex(), "test");
    assertEquals("Default Spieler", file.index(StandardIndex.PLAYERS).name());
    assertEquals("Default Kommentator", file.index(StandardIndex.ANNOTATORS).name());
    assertEquals("Default Partietitle", file.index(StandardIndex.GAME_TAGS).name());
    // These two are after the new index in the catalog, and have the same type and number
    assertEquals("Texttitel", file.index(StandardIndex.TEXT_TITLES).name());
    assertEquals("Analysen", file.index(StandardIndex.ANALYSIS_TITLES).name());
  }

  @Test
  void theStandardIndexesAreFoundInACatalogWithoutTheNewOne() {
    MemoryByteStore store = new MemoryByteStore();
    SortIndexFile.create(store);
    SortIndexFile file = new SortIndexFile(store, "test");
    assertTrue(file.unknownIndexes().isEmpty());
    assertEquals("Default Partietitle", file.index(StandardIndex.GAME_TAGS).name());
    assertEquals("Texttitel", file.index(StandardIndex.TEXT_TITLES).name());
    assertEquals("Analysen", file.index(StandardIndex.ANALYSIS_TITLES).name());
  }

  @Test
  void changesGoToTheIndexAndLeaveTheUnknownOneAlone() {
    MemoryByteStore store = withTimeControlIndex();
    byte[] before = catalogEntry(store, NEW_INDEX_SLOT);

    SortIndexFile file = new SortIndexFile(store, "test");
    Map<Integer, Entity> tags = new HashMap<>();
    tags.put(0, GameTag.of("A title"));
    tags.put(1, GameTag.of("Another title"));
    SortIndexFile.NodeComparator comparator = EntityOrder.nodeComparator(tags::get);
    for (int id : tags.keySet()) {
      file.index(StandardIndex.TEXT_TITLES).insert(id, EntityOrder.key(tags.get(id)), comparator);
    }

    assertArrayEquals(before, catalogEntry(store, NEW_INDEX_SLOT));
    SortIndexFile reopened = new SortIndexFile(store, "test");
    assertEquals(2, reopened.index(StandardIndex.TEXT_TITLES).count());
    assertEquals(0, reopened.index(StandardIndex.ANALYSIS_TITLES).count());
    assertEquals(0, reopened.index(StandardIndex.GAME_TAGS).count());
    assertEquals(0, reopened.unknownIndexes().getFirst().count());
  }
}

package se.yarin.morphy.cb2.entities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.jupiter.api.Test;
import se.yarin.morphy.cb2.storage.MemoryByteStore;

/** The header of the {@code .2lid} file comes in more than one shape. */
class EntityFileLayoutTest {

  /**
   * The header of a file written by newer versions of ChessBase: 216 bytes, the container of the
   * type that never holds anything gone, and more pairs of unknown meaning at the end.
   */
  private static byte[] newerHeader() {
    int[] containers = {1024, 1120, 220, 0, 314, 532};
    ByteBuffer header = ByteBuffer.allocate(216).order(ByteOrder.BIG_ENDIAN);
    header.putInt(216).putInt(containers.length);
    for (int container : containers) {
      header.putInt(container).putLong(0).putLong(-1);
    }
    int[][] pairs = {{-1, 1}, {0, 1}, {1, 1}, {2, 1}, {3, 2}, {4, 1}, {5, 1}, {-1, 1}, {-1, 1}, {-1, 1}, {-1, 1}};
    for (int[] pair : pairs) {
      header.putInt(pair[0]).putInt(pair[1]);
    }
    return header.array();
  }

  /** A header that declares one more type than is known, after the ones that are. */
  private static byte[] headerWithAnotherType() {
    int[] containers = {1024, 1120, 220, 1024, 314, 532, 50};
    ByteBuffer header = ByteBuffer.allocate(184).order(ByteOrder.BIG_ENDIAN);
    header.putInt(184).putInt(containers.length);
    for (int container : containers) {
      header.putInt(container).putLong(0).putLong(-1);
    }
    return header.array();
  }

  /**
   * Adds entities to a file and reads them back, and checks that the file ends where the last one
   * must be, which is inside the container of a game tag in the second block.
   *
   * @param gameTagOffset where that container is: the header, one block and the containers before it
   */
  private static void roundTrips(EntityFile file, int gameTagOffset) {
    assertEquals(0, file.add(Player.of("Carlsen", "Magnus")));
    assertEquals(1, file.add(Player.of("Caruana", "Fabiano")));
    assertEquals(0, file.add(GameTag.of("A title")));
    assertEquals(1, file.add(GameTag.of("Another title")));
    assertEquals(Player.of("Caruana", "Fabiano"), file.get(EntityType.PLAYER, 1));
    assertEquals(GameTag.of("Another title"), file.get(EntityType.GAME_TAG, 1));
    assertNotNull(file.get(EntityType.PLAYER, 0));
    assertEquals(2, file.count(EntityType.PLAYER));
    assertEquals(2, file.count(EntityType.GAME_TAG));
    long size = file.store().size();
    assertTrue(size >= gameTagOffset && size < gameTagOffset + 532, "the file ends at " + size);
  }

  @Test
  void theHeaderOfNewerVersionsIsRead() {
    EntityFile file = new EntityFile(new MemoryByteStore(newerHeader()), "test");
    assertEquals(0, file.containerSize(EntityType.UNKNOWN));
    assertEquals(532, file.containerSize(EntityType.GAME_TAG));
    // The header, one block of 1024 + 1120 + 220 + 0 + 314 + 532, then the containers before the tag
    roundTrips(file, 216 + 3210 + (1024 + 1120 + 220 + 0 + 314));
  }

  @Test
  void aTypeThatIsNotKnownIsSkippedOver() {
    EntityFile file = new EntityFile(new MemoryByteStore(headerWithAnotherType()), "test");
    // The header, one block of seven containers, then the containers before the tag
    roundTrips(file, 184 + (1024 + 1120 + 220 + 1024 + 314 + 532 + 50) + (1024 + 1120 + 220 + 1024 + 314));
  }
}

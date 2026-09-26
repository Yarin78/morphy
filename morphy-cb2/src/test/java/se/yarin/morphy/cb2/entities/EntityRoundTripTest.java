package se.yarin.morphy.cb2.entities;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import se.yarin.morphy.api.AccessMode;
import se.yarin.morphy.cb2.TestDatabases;
import se.yarin.morphy.cb2.storage.ByteStore;

/** Every entity decodes, and encodes back to the same bytes. */
class EntityRoundTripTest {

  @Test
  void sampleDatabaseRoundTrips() {
    Map<EntityType, Integer> counts = roundTrip(TestDatabases.wch2());
    System.out.println("wch2: " + counts);
    assertTrue(counts.get(EntityType.PLAYER) > 50);
    assertTrue(counts.get(EntityType.TOURNAMENT) > 20);
  }

  @Test
  void scratchDatabasesRoundTrip() {
    for (String name : new String[] {"probe", "reveng1"}) {
      File file = new File("../test-databases/scratch/" + name + ".2cbh");
      Assumptions.assumeTrue(file.exists(), "scratch database not present");
      System.out.println(name + ": " + roundTrip(file));
    }
  }

  private static Map<EntityType, Integer> roundTrip(File file) {
    Map<EntityType, Integer> counts = new EnumMap<>(EntityType.class);
    try (EntityFile entities =
        new EntityFile(
            ByteStore.open(TestDatabases.sibling(file, ".2lid"), AccessMode.READ_ONLY), "2lid")) {
      for (EntityType type : EntityType.values()) {
        int n = 0;
        for (int id = 0; id < entities.count(type); id++) {
          Entity entity = entities.get(type, id);
          if (entity == null) {
            continue;
          }
          assertArrayEquals(entities.readRecord(type, id), entity.encode(), type + " " + id);
          n++;
        }
        counts.put(type, n);
      }
    }
    return counts;
  }
}

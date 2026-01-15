package se.yarin.morphy.entities;

import org.junit.Test;
import se.yarin.chess.Date;
import se.yarin.morphy.DatabaseMode;
import se.yarin.morphy.ResourceLoader;
import se.yarin.morphy.games.GameHeader;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.*;

public class TournamentExtraTest {

  private final Random random = new Random();

  private TournamentExtra createItem(double latitude, double longitude, Date endDate) {
    return ImmutableTournamentExtra.builder()
        .latitude(latitude)
        .longitude(longitude)
        .endDate(endDate)
        .build();
  }

  private TournamentExtra createDummyItem() {
    return createItem(
        random.nextDouble(),
        random.nextDouble(),
        new Date(2020, random.nextInt(12) + 1, random.nextInt(28) + 1));
  }

  @Test
  public void testSerialization() {
    ImmutableTournamentExtra newTournamentExtra =
        ImmutableTournamentExtra.builder()
            .latitude(1.2)
            .longitude(3.4)
            .endDate(new Date(2016, 7, 13))
            .tiebreakRules(Arrays.asList(TiebreakRule.RR_NUM_WINS, TiebreakRule.RR_POINT_GROUP))
            .build();

    TournamentExtraStorage extraSerializer = new TournamentExtraStorage();

    ByteBuffer bufExtra = ByteBuffer.allocate(1000);
    extraSerializer.serializeItem(newTournamentExtra, bufExtra, TournamentExtraHeader.empty());
    bufExtra.flip();

    TournamentExtra extra =
        extraSerializer.deserializeItem(0, bufExtra, TournamentExtraHeader.empty());

    assertEquals(1.2, extra.latitude(), 1e-6);
    assertEquals(3.4, extra.longitude(), 1e-6);
    assertEquals(new Date(2016, 7, 13), extra.endDate());
    assertEquals(2, extra.tiebreakRules().size());
    assertEquals(TiebreakRule.RR_NUM_WINS, extra.tiebreakRules().get(0));
    assertEquals(TiebreakRule.RR_POINT_GROUP, extra.tiebreakRules().get(1));
  }

  @Test
  public void createEmptyStorage() {
    TournamentExtraStorage storage = new TournamentExtraStorage();
    assertEquals(0, storage.numEntries());
  }

  @Test
  public void addAndRetrieveItems() {
    TournamentExtraStorage storage = new TournamentExtraStorage();
    TournamentExtra d1 = createDummyItem();
    storage.put(0, d1);
    assertEquals(1, storage.numEntries());
    TournamentExtra d2 = createDummyItem();
    storage.put(1, d2);
    assertEquals(2, storage.numEntries());

    TournamentExtra e1 = storage.get(0);
    assertEquals(d1, e1);
    TournamentExtra e2 = storage.get(1);
    assertEquals(d2, e2);
  }

  @Test
  public void addItemBeyondLastIndex() {
    TournamentExtraStorage storage = new TournamentExtraStorage();
    TournamentExtra d1 = createDummyItem();
    storage.put(0, d1);
    assertEquals(1, storage.numEntries());
    TournamentExtra d2 = createDummyItem();
    storage.put(7, d2);
    assertEquals(8, storage.numEntries());

    TournamentExtra e1 = storage.get(0);
    assertEquals(d1, e1);
    TournamentExtra ee = storage.get(3);
    assertEquals(TournamentExtra.empty(), ee);
    TournamentExtra e2 = storage.get(7);
    assertEquals(d2, e2);
  }

  @Test
  public void getItemBeyondLastIndex() {
    TournamentExtraStorage storage = new TournamentExtraStorage();

    assertEquals(0, storage.numEntries());
    assertEquals(TournamentExtra.empty(), storage.get(5));
    assertEquals(0, storage.numEntries()); // Ensure no fake entries was added on read

    TournamentExtra d1 = createDummyItem();
    storage.put(0, d1);

    // Still works after one item was added
    assertEquals(1, storage.numEntries());
    assertEquals(TournamentExtra.empty(), storage.get(5));
    assertEquals(1, storage.numEntries()); // Ensure no fake entries was added on read
  }

  @Test
  public void getItemFromOlderVersion() throws IOException {
    File file =
        ResourceLoader.materializeDatabaseStream(GameHeader.class, "upgradable", List.of(".cbtt"));
    TournamentExtraStorage storage =
        TournamentExtraStorage.open(file, DatabaseMode.READ_ONLY, null);
    assertEquals(2, storage.getStorageVersion());

    assertEquals(55.44959, storage.get(7).latitude(), 1e-6);
    assertEquals(10.65779, storage.get(7).longitude(), 1e-6);
  }

  @Test
  public void testUpgradeStorageVersion() throws IOException {
    File oldStorageFile =
        ResourceLoader.materializeDatabaseStream(GameHeader.class, "upgradable", List.of(".cbtt"));
    TournamentExtraHeader oldHeader = TournamentExtraStorage.peekHeader(oldStorageFile);
    assertEquals(2, oldHeader.version());
    int numRecords = oldHeader.highestIndex() + 1;
    long oldFileSize = oldStorageFile.length();

    TournamentExtraStorage.upgrade(oldStorageFile);

    assertEquals(oldFileSize + 4L * numRecords, oldStorageFile.length());

    TournamentExtraStorage upgradedStorage =
        TournamentExtraStorage.open(oldStorageFile, DatabaseMode.READ_ONLY, null);
    assertEquals(TournamentExtraHeader.DEFAULT_HEADER_VERSION, upgradedStorage.getStorageVersion());

    assertEquals(55.44959, upgradedStorage.get(7).latitude(), 1e-6);
    assertEquals(10.65779, upgradedStorage.get(7).longitude(), 1e-6);
  }

  @Test
  public void testUpgradeMissingStorage() throws IOException {
    File file = File.createTempFile("dummy", ".cbtt");
    file.delete();

    TournamentExtraStorage.upgrade(file);

    assertTrue(file.exists());
    TournamentExtraStorage upgradedStorage =
        TournamentExtraStorage.open(file, DatabaseMode.READ_ONLY, null);
    assertEquals(TournamentExtraHeader.DEFAULT_HEADER_VERSION, upgradedStorage.getStorageVersion());
    assertEquals(0, upgradedStorage.numEntries());
  }
}

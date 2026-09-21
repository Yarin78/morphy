package se.yarin.morphy.games;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import se.yarin.morphy.ResourceLoader;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.Assert.assertEquals;

public class TopGamesStorageTest {
  @Rule public TemporaryFolder folder = new TemporaryFolder();

  private File topGamesStorageFile;

  @Before
  public void setupTopGamesStorageTest() throws IOException {
    topGamesStorageFile = folder.newFile("testbase.flags");
    topGamesStorageFile.delete();
  }

  @Test
  public void read() throws IOException {
    File flagsFile = ResourceLoader.materializeDatabaseStream(TopGamesStorage.class, "topgames");
    TopGamesStorage topGamesStorage = TopGamesStorage.open(flagsFile, null);

    int topGameCount = 0;
    for (int gameId = 1; gameId <= topGamesStorage.count(); gameId++) {
      if (topGamesStorage.isTopGame(gameId)) {
        topGameCount += 1;
      }
    }

    assertEquals(127, topGameCount);

    assertEquals(TopGamesStorage.TopGameStatus.IS_TOP_GAME, topGamesStorage.getGameStatus(108));
    assertEquals(TopGamesStorage.TopGameStatus.UNKNOWN, topGamesStorage.getGameStatus(109));
    assertEquals(TopGamesStorage.TopGameStatus.IS_TOP_GAME, topGamesStorage.getGameStatus(110));
    assertEquals(TopGamesStorage.TopGameStatus.IS_NOT_TOP_GAME, topGamesStorage.getGameStatus(111));
    assertEquals(TopGamesStorage.TopGameStatus.IS_NOT_TOP_GAME, topGamesStorage.getGameStatus(112));
    assertEquals(TopGamesStorage.TopGameStatus.IS_TOP_GAME, topGamesStorage.getGameStatus(113));
    assertEquals(TopGamesStorage.TopGameStatus.UNKNOWN, topGamesStorage.getGameStatus(114));
    assertEquals(TopGamesStorage.TopGameStatus.IS_TOP_GAME, topGamesStorage.getGameStatus(115));
    assertEquals(TopGamesStorage.TopGameStatus.IS_TOP_GAME, topGamesStorage.getGameStatus(116));
  }

  @Test
  public void createEmptyStorage() throws IOException {
    TopGamesStorage storage = TopGamesStorage.create(topGamesStorageFile, null);
    assertEquals(0, storage.count());
    storage.close();

    storage = TopGamesStorage.open(topGamesStorageFile, null);
    assertEquals(0, storage.count());
    storage.close();
  }

  @Test
  public void updateSingleGame() throws IOException {
    TopGamesStorage storage = TopGamesStorage.create(topGamesStorageFile, null);
    storage.putGameStatus(1, TopGamesStorage.TopGameStatus.IS_TOP_GAME);
    assertEquals(1, storage.count());
    assertEquals(TopGamesStorage.TopGameStatus.IS_TOP_GAME, storage.getGameStatus(1));
    assertEquals(TopGamesStorage.TopGameStatus.UNKNOWN, storage.getGameStatus(2));
    storage.close();

    storage = TopGamesStorage.open(topGamesStorageFile, null);
    assertEquals(256, storage.count());
    assertEquals(TopGamesStorage.TopGameStatus.IS_TOP_GAME, storage.getGameStatus(1));
    assertEquals(TopGamesStorage.TopGameStatus.UNKNOWN, storage.getGameStatus(2));
    storage.close();
  }

  @Test
  public void updateMultipleGames() throws IOException {
    TopGamesStorage storage = TopGamesStorage.create(topGamesStorageFile, null);
    storage.putGameStatuses(
        Map.of(
            7, TopGamesStorage.TopGameStatus.IS_TOP_GAME,
            49, TopGamesStorage.TopGameStatus.IS_NOT_TOP_GAME,
            5193, TopGamesStorage.TopGameStatus.IS_TOP_GAME,
            5820, TopGamesStorage.TopGameStatus.IS_NOT_TOP_GAME));
    assertEquals(5820, storage.count());
    storage.close();

    storage = TopGamesStorage.open(topGamesStorageFile, null);
    assertEquals(5888, storage.count());
    assertEquals(TopGamesStorage.TopGameStatus.IS_TOP_GAME, storage.getGameStatus(7));
    assertEquals(TopGamesStorage.TopGameStatus.IS_NOT_TOP_GAME, storage.getGameStatus(49));
    assertEquals(TopGamesStorage.TopGameStatus.UNKNOWN, storage.getGameStatus(2013));
    assertEquals(TopGamesStorage.TopGameStatus.IS_TOP_GAME, storage.getGameStatus(5193));
    assertEquals(TopGamesStorage.TopGameStatus.IS_NOT_TOP_GAME, storage.getGameStatus(5820));
    storage.close();
  }

  @Test
  public void updateManyGamesAtRandomInBatches() throws IOException {
    Random random = new Random();
    TopGamesStorage.TopGameStatus[] expected = new TopGamesStorage.TopGameStatus[200000];
    Arrays.fill(expected, TopGamesStorage.TopGameStatus.UNKNOWN);

    TopGamesStorage storage = TopGamesStorage.create(topGamesStorageFile, null);

    // Run enough to ensure we exceed the in memory chunk
    // TODO: Add config to make the in memory chunk smaller in tests
    for (int iter = 0; iter < 100; iter++) {
      int batchSize = random.nextInt(200) + 100;
      int maxGameId = 1000 * (iter + 1);
      HashMap<Integer, TopGamesStorage.TopGameStatus> update = new HashMap<>();
      for (int i = 0; i < batchSize; i++) {
        int gameId = random.nextInt(maxGameId);
        TopGamesStorage.TopGameStatus status;
        switch (random.nextInt(3)) {
          case 0 -> status = TopGamesStorage.TopGameStatus.UNKNOWN;
          case 1 -> status = TopGamesStorage.TopGameStatus.IS_NOT_TOP_GAME;
          case 2 -> status = TopGamesStorage.TopGameStatus.IS_TOP_GAME;
          default -> throw new IllegalStateException("Unexpected value");
        }
        update.put(gameId, status);
        expected[gameId] = status;
      }
      storage.putGameStatuses(update);

      verify(storage, expected);
      storage.close();

      storage = TopGamesStorage.open(topGamesStorageFile, null);
      verify(storage, expected);
    }

    storage.close();
  }

  /** Creates a .flags file that uses 3 bits per game, as ChessBase 12 did, with room for 160 games. */
  private void createThreeBitFile() throws IOException {
    ByteBuffer buf = ByteBuffer.allocate(12 + 16 * 4);
    buf.putInt(0x0F010B09);
    buf.putInt(16); // Capacity in 32-bit ints
    buf.putInt(3); // Bits per game
    Files.write(topGamesStorageFile.toPath(), buf.array());
  }

  @Test
  public void threeBitsPerGame() throws IOException {
    createThreeBitFile();
    Random random = new Random();
    TopGamesStorage.TopGameStatus[] expected = new TopGamesStorage.TopGameStatus[1000];
    Arrays.fill(expected, TopGamesStorage.TopGameStatus.UNKNOWN);

    TopGamesStorage storage = TopGamesStorage.open(topGamesStorageFile, null);
    assertEquals(170, storage.count()); // 16 ints of 32 bits, with room for 170 games
    HashMap<Integer, TopGamesStorage.TopGameStatus> update = new HashMap<>();
    for (int gameId = 1; gameId <= 170; gameId++) {
      TopGamesStorage.TopGameStatus status =
          TopGamesStorage.TopGameStatus.values()[random.nextInt(4)];
      update.put(gameId, status);
      expected[gameId] = status;
    }
    storage.putGameStatuses(update);
    verify(storage, expected);
    storage.close();

    storage = TopGamesStorage.open(topGamesStorageFile, null);
    verify(storage, expected);
    storage.close();

    // The first bit of a game is set for a top game and the second when it has been evaluated;
    // the third bit is never set
    byte[] data = Files.readAllBytes(topGamesStorageFile.toPath());
    assertEquals(12 + ByteBuffer.wrap(data).getInt(4) * 4, data.length);
    assertEquals(3, ByteBuffer.wrap(data).getInt(8));
    for (int gameId = 1; gameId <= 170; gameId++) {
      int bit = gameId * 3;
      int top = (data[12 + bit / 8] >> (bit % 8)) & 1;
      int evaluated = (data[12 + (bit + 1) / 8] >> ((bit + 1) % 8)) & 1;
      int third = (data[12 + (bit + 2) / 8] >> ((bit + 2) % 8)) & 1;
      assertEquals(expected[gameId].ordinal(), top + 2 * evaluated);
      assertEquals(0, third);
    }
  }

  @Test
  public void threeBitsPerGameIsKeptWhenGrowing() throws IOException {
    createThreeBitFile();
    TopGamesStorage storage = TopGamesStorage.open(topGamesStorageFile, null);
    storage.putGameStatus(1000, TopGamesStorage.TopGameStatus.IS_TOP_GAME);
    storage.putGameStatus(999, TopGamesStorage.TopGameStatus.IS_NOT_TOP_GAME);
    storage.close();

    byte[] data = Files.readAllBytes(topGamesStorageFile.toPath());
    assertEquals(3, ByteBuffer.wrap(data).getInt(8));
    int capacity = ByteBuffer.wrap(data).getInt(4);
    assertEquals(0, capacity % 16);
    assertEquals(12 + capacity * 4, data.length);

    storage = TopGamesStorage.open(topGamesStorageFile, null);
    assertEquals(TopGamesStorage.TopGameStatus.IS_TOP_GAME, storage.getGameStatus(1000));
    assertEquals(TopGamesStorage.TopGameStatus.IS_NOT_TOP_GAME, storage.getGameStatus(999));
    assertEquals(TopGamesStorage.TopGameStatus.UNKNOWN, storage.getGameStatus(998));
    storage.close();
  }

  private void verify(TopGamesStorage storage, TopGamesStorage.TopGameStatus[] expected) {
    for (int i = 1; i <= storage.count(); i++) {
      assertEquals(expected[i], storage.getGameStatus(i));
    }
  }
}

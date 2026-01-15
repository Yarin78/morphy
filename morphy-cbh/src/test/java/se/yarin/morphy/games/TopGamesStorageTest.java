package se.yarin.morphy.games;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import se.yarin.morphy.ResourceLoader;

import java.io.File;
import java.io.IOException;
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

  private void verify(TopGamesStorage storage, TopGamesStorage.TopGameStatus[] expected) {
    for (int i = 1; i <= storage.count(); i++) {
      assertEquals(expected[i], storage.getGameStatus(i));
    }
  }
}

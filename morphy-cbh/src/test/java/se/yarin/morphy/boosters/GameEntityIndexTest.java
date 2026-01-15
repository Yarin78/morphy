package se.yarin.morphy.boosters;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import se.yarin.morphy.util.CBUtil;
import se.yarin.morphy.Database;
import se.yarin.morphy.ResourceLoader;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.entities.Player;
import se.yarin.morphy.validation.Validator;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.junit.Assert.assertEquals;
import static se.yarin.morphy.validation.Validator.Checks.*;

public class GameEntityIndexTest {

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  @Test
  public void validateWorldCh() {
    Database db = ResourceLoader.openWorldChDatabase();

    new Validator()
        .validate(
            db,
            EnumSet.of(
                ENTITY_DB_INTEGRITY,
                ENTITY_SORT_ORDER,
                ENTITY_STATISTICS,
                ENTITY_PLAYERS,
                ENTITY_TOURNAMENTS,
                ENTITY_ANNOTATORS,
                ENTITY_SOURCES,
                ENTITY_TEAMS,
                ENTITY_GAME_TAGS,
                GAME_ENTITY_INDEX),
            true,
            true,
            false);
  }

  @Test
  public void iterateIndex() {
    Database db = ResourceLoader.openWorldChDatabase();

    GameEntityIndex index = db.gameEntityIndex(EntityType.PLAYER);
    assert index != null;

    try (var txn = db.playerIndex().beginReadTransaction()) {
      for (Player player : txn.iterable()) {
        List<Integer> gameIds = index.getGameIds(player.id(), EntityType.PLAYER, true);
        List<Integer> iteratedGameIds =
            StreamSupport.stream(
                    index.iterable(player.id(), EntityType.PLAYER, true).spliterator(), false)
                .collect(Collectors.toList());
        assertEquals(gameIds, iteratedGameIds);

        List<Integer> gameIdsDedup = index.getGameIds(player.id(), EntityType.PLAYER, false);
        List<Integer> iteratedGameIdsDedup =
            StreamSupport.stream(
                    index.iterable(player.id(), EntityType.PLAYER, false).spliterator(), false)
                .collect(Collectors.toList());
        assertEquals(gameIdsDedup, iteratedGameIdsDedup);
      }
    }
  }

  @Test
  public void iterateDedup() {
    GameEntityIndex index =
        new GameEntityIndex(Arrays.asList(EntityType.PLAYER, EntityType.SOURCE));
    TreeMap<Integer, Integer> map =
        new TreeMap<>() {
          {
            put(3, 2);
            put(4, 1);
            put(8, 4);
            put(5, 4);
            put(11, 5);
            put(6, 3);
            put(2, 3);
            put(15, 2);
            put(9, 7);
          }
        };
    index.updateEntity(0, EntityType.PLAYER, map);

    List<Integer> iteratedGameIdsAll =
        StreamSupport.stream(index.iterable(0, EntityType.PLAYER, true).spliterator(), false)
            .collect(Collectors.toList());
    assertEquals(
        Arrays.asList(
            2, 2, 2, 3, 3, 4, 5, 5, 5, 5, 6, 6, 6, 8, 8, 8, 8, 9, 9, 9, 9, 9, 9, 9, 11, 11, 11, 11,
            11, 15, 15),
        iteratedGameIdsAll);

    List<Integer> iteratedGameIdsDedup =
        StreamSupport.stream(index.iterable(0, EntityType.PLAYER, false).spliterator(), false)
            .collect(Collectors.toList());
    assertEquals(Arrays.asList(2, 3, 4, 5, 6, 8, 9, 11, 15), iteratedGameIdsDedup);
  }

  @Test
  public void updateEmptyIndex() {
    GameEntityIndex index =
        new GameEntityIndex(Arrays.asList(EntityType.PLAYER, EntityType.SOURCE));
    TreeMap<Integer, Integer> map =
        new TreeMap<>() {
          {
            put(2, 1);
            put(3, 0);
            put(4, 2);
          }
        };
    index.updateEntity(0, EntityType.PLAYER, map);
    assertEquals(Arrays.asList(2, 4, 4), index.getGameIds(0, EntityType.PLAYER, true));
  }

  private GameEntityIndex setupFileGameEntityIndex() throws IOException {
    File file = folder.newFile();
    file.delete();
    return GameEntityIndex.create(
        CBUtil.fileWithExtension(file, ".cit"), CBUtil.fileWithExtension(file, ".cib"), null);
  }

  @Test
  public void updateEmptyIndexOnDisk() throws IOException {
    GameEntityIndex index = setupFileGameEntityIndex();
    TreeMap<Integer, Integer> map =
        new TreeMap<>() {
          {
            put(2, 1);
            put(3, 0);
            put(4, 2);
          }
        };
    index.updateEntity(0, EntityType.PLAYER, map);
    assertEquals(Arrays.asList(2, 4, 4), index.getGameIds(0, EntityType.PLAYER, true));
  }

  @Test
  public void updateEntityBeyondLastEntity() throws IOException {
    GameEntityIndex index = setupFileGameEntityIndex();
    TreeMap<Integer, Integer> map =
        new TreeMap<>() {
          {
            put(2, 1);
            put(3, 0);
            put(4, 2);
          }
        };
    index.updateEntity(4, EntityType.PLAYER, map);
    assertEquals(Arrays.asList(2, 4, 4), index.getGameIds(4, EntityType.PLAYER, true));
  }

  @Test
  public void getGamesFromEmptyEntity() {
    GameEntityIndex index =
        new GameEntityIndex(Arrays.asList(EntityType.PLAYER, EntityType.SOURCE));
    index.updateEntity(0, EntityType.PLAYER, Map.of(2, 1));
    assertEquals(Collections.emptyList(), index.getGameIds(0, EntityType.SOURCE, true));
    assertEquals(Collections.emptyList(), index.getGameIds(1, EntityType.PLAYER, true));
  }

  @Test
  public void updateEmptyIndexWithEntityBeyondLast() {
    GameEntityIndex index =
        new GameEntityIndex(Arrays.asList(EntityType.PLAYER, EntityType.SOURCE));
    TreeMap<Integer, Integer> map =
        new TreeMap<>() {
          {
            put(2, 1);
          }
        };
    index.updateEntity(5, EntityType.PLAYER, map);
    assertEquals(Collections.emptyList(), index.getGameIds(2, EntityType.PLAYER, true));
    assertEquals(Collections.emptyList(), index.getGameIds(4, EntityType.PLAYER, true));
    assertEquals(Collections.singletonList(2), index.getGameIds(5, EntityType.PLAYER, true));
  }

  @Test
  public void updateEntityIndexWithAddsMultipleTimes() {
    GameEntityIndex index =
        new GameEntityIndex(Arrays.asList(EntityType.PLAYER, EntityType.SOURCE));
    index.updateEntity(0, EntityType.PLAYER, Map.of(2, 1));
    index.updateEntity(0, EntityType.PLAYER, Map.of(9, 1));
    index.updateEntity(0, EntityType.PLAYER, Map.of(5, 2));
    index.updateEntity(0, EntityType.SOURCE, Map.of(8, 1));
    index.updateEntity(0, EntityType.SOURCE, Map.of(4, 1));
    index.updateEntity(1, EntityType.PLAYER, Map.of(5, 1));
    index.updateEntity(1, EntityType.SOURCE, Map.of(3, 2));
    index.updateEntity(1, EntityType.PLAYER, Map.of(2, 1));
    index.updateEntity(0, EntityType.SOURCE, Map.of(1, 1));
    index.updateEntity(0, EntityType.PLAYER, Map.of(3, 1));

    assertEquals(Arrays.asList(2, 3, 5, 5, 9), index.getGameIds(0, EntityType.PLAYER, true));
    assertEquals(Arrays.asList(2, 5), index.getGameIds(1, EntityType.PLAYER, true));
    assertEquals(Arrays.asList(1, 4, 8), index.getGameIds(0, EntityType.SOURCE, true));
    assertEquals(Arrays.asList(3, 3), index.getGameIds(1, EntityType.SOURCE, true));
  }

  @Test
  public void updateEntityIndexWithDeletes() {
    GameEntityIndex index =
        new GameEntityIndex(Arrays.asList(EntityType.PLAYER, EntityType.SOURCE));
    index.updateEntity(0, EntityType.PLAYER, Map.of(2, 1));
    index.updateEntity(0, EntityType.PLAYER, Map.of(5, 2));
    index.updateEntity(0, EntityType.PLAYER, Map.of(9, 1));
    index.updateEntity(0, EntityType.PLAYER, Map.of(3, 1));
    assertEquals(Arrays.asList(2, 3, 5, 5, 9), index.getGameIds(0, EntityType.PLAYER, true));
    index.updateEntity(0, EntityType.PLAYER, Map.of(5, 1));
    assertEquals(Arrays.asList(2, 3, 5, 9), index.getGameIds(0, EntityType.PLAYER, true));
    index.updateEntity(0, EntityType.PLAYER, Map.of(8, 1));
    index.updateEntity(0, EntityType.PLAYER, Map.of(5, 0));
    index.updateEntity(0, EntityType.PLAYER, Map.of(2, 0));
    assertEquals(Arrays.asList(3, 8, 9), index.getGameIds(0, EntityType.PLAYER, true));

    assertEquals(1, index.getNumBlocks());
    assertEquals(0, index.getDeletedBlockIds().size());

    TreeMap<Integer, Integer> tm =
        new TreeMap<>() {
          {
            put(3, 0);
            put(8, 0);
            put(9, 0);
            put(15, 1);
          }
        };
    index.updateEntity(0, EntityType.PLAYER, tm);
    assertEquals(Collections.singletonList(15), index.getGameIds(0, EntityType.PLAYER, true));
    assertEquals(1, index.getNumBlocks());
    assertEquals(0, index.getDeletedBlockIds().size());
  }

  @Test
  public void randomLargeUpdatesOnSingleEntity() {
    GameEntityIndex index =
        new GameEntityIndex(Arrays.asList(EntityType.PLAYER, EntityType.SOURCE));
    Random random = new Random(0);
    int[] expectedCount = new int[201];
    int total = 0;
    for (int batch = 0; batch < 500; batch++) {
      TreeMap<Integer, Integer> updateBatch = new TreeMap<>();
      int newTotal = total;
      for (int gameId = 1; gameId <= 200; gameId++) {
        if (random.nextDouble() < 0.1) {
          double p = random.nextDouble();
          int cnt = 1;
          if (total < 180) {
            // Ensure there are a lot of games
            if (p < 0.2) {
              cnt = 0;
            } else if (p < 0.25) {
              cnt = 2;
            }
          } else {
            // Steady state
            if (p < 0.45) {
              cnt = 0;
            } else if (p < 0.50) {
              cnt = 2;
            }
          }
          updateBatch.put(gameId, cnt);
          newTotal -= expectedCount[gameId];
          expectedCount[gameId] = cnt;
          newTotal += cnt;
        }
      }
      total = newTotal;

      index.updateEntity(0, EntityType.PLAYER, updateBatch);

      ArrayList<Integer> expectedList = new ArrayList<>();
      for (int gameId = 1; gameId <= 200; gameId++) {
        for (int i = 0; i < expectedCount[gameId]; i++) {
          expectedList.add(gameId);
        }
      }
      List<Integer> actual = index.getGameIds(0, EntityType.PLAYER, true);
      assertEquals(expectedList, actual);
    }
  }

  @Test
  public void testDeletedBlocksAreReclaimed() {
    GameEntityIndex index =
        new GameEntityIndex(Arrays.asList(EntityType.PLAYER, EntityType.SOURCE));

    index.updateEntity(0, EntityType.PLAYER, Map.of(2, 1));
    index.updateEntity(1, EntityType.PLAYER, Map.of(4, 1));
    index.updateEntity(2, EntityType.PLAYER, Map.of(3, 1));
    index.updateEntity(0, EntityType.SOURCE, Map.of(5, 1));
    index.updateEntity(1, EntityType.SOURCE, Map.of(2, 1));
    index.updateEntity(2, EntityType.SOURCE, Map.of(1, 1));

    assertEquals(0, index.getDeletedBlockIds().size());
    assertEquals(6, index.getNumBlocks());

    index.updateEntity(0, EntityType.SOURCE, Map.of(5, 0));
    assertEquals(1, index.getDeletedBlockIds().size());
    assertEquals(6, index.getNumBlocks());

    index.updateEntity(1, EntityType.PLAYER, Map.of(4, 0));
    assertEquals(2, index.getDeletedBlockIds().size());
    assertEquals(6, index.getNumBlocks());

    index.updateEntity(2, EntityType.SOURCE, Map.of(7, 20));
    assertEquals(1, index.getDeletedBlockIds().size());
    assertEquals(6, index.getNumBlocks());

    index.updateEntity(2, EntityType.PLAYER, Map.of(4, 1));
    TreeMap<Integer, Integer> tm =
        new TreeMap<>() {
          {
            put(3, 0);
            put(4, 0);
          }
        };
    index.updateEntity(2, EntityType.PLAYER, tm);
    assertEquals(2, index.getDeletedBlockIds().size());
    assertEquals(6, index.getNumBlocks());

    index.updateEntity(3, EntityType.PLAYER, Map.of(1, 30)); // 3 blocks
    assertEquals(0, index.getDeletedBlockIds().size());
    assertEquals(7, index.getNumBlocks());
  }

  @Test
  public void testUpdateEntityWithNoGamesWithNoGames() {
    GameEntityIndex index =
        new GameEntityIndex(Arrays.asList(EntityType.PLAYER, EntityType.SOURCE));
    assertEquals(0, index.getNumBlocks());

    index.updateEntity(0, EntityType.PLAYER, Map.of(5, 0));

    assertEquals(0, index.getNumBlocks());
    assertEquals(Collections.emptyList(), index.getGameIds(0, EntityType.PLAYER, true));
  }

  @Test
  public void testUpdateCausingTailBlockToDisappear() {
    GameEntityIndex index =
        new GameEntityIndex(Arrays.asList(EntityType.PLAYER, EntityType.SOURCE));

    index.updateEntity(0, EntityType.PLAYER, Map.of(1, 13, 2, 3, 3, 5));
    assertEquals(2, index.getNumBlocks());

    index.updateEntity(0, EntityType.PLAYER, Map.of(2, 0, 3, 0));
    assertEquals(Collections.singletonList(1), index.getGameIds(0, EntityType.PLAYER, false));
  }
}

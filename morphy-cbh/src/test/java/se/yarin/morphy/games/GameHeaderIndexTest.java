package se.yarin.morphy.games;

import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import se.yarin.chess.GameResult;
import se.yarin.morphy.DatabaseMode;
import se.yarin.morphy.ResourceLoader;
import se.yarin.morphy.storage.ItemStorageFilter;
import se.yarin.util.ByteBufferUtil;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public class GameHeaderIndexTest {

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  private File gameHeaderFile;

  @Before
  public void setupEntityTest() throws IOException {
    gameHeaderFile =
        ResourceLoader.materializeDatabaseStream(
            GameHeaderIndex.class, "cbh_test", List.of(".cbh"));
  }

  @Test
  public void createInMemoryIndex() {
    GameHeaderIndex base = new GameHeaderIndex();
    assertEquals(0, base.count());
  }

  @Test
  public void createIndex() throws IOException {
    File file = folder.newFile("newbase.cbh");
    file.delete();
    GameHeaderIndex index = GameHeaderIndex.create(file, null);
    assertEquals(0, index.count());
    index.close();
  }

  @Test
  public void getSimpleGame() throws IOException {
    File cbh_only = ResourceLoader.materializeDatabaseStream(getClass(), "cbh_only");
    GameHeaderIndex index = GameHeaderIndex.open(cbh_only, null);
    assertEquals(4, index.count());

    GameHeader game1 = index.getGameHeader(1);
    assertEquals(1, game1.id());
    assertEquals(0, game1.whitePlayerId());
    assertEquals(2200, game1.whiteElo());
    assertEquals(2, game1.round());
    assertEquals(1, game1.subRound());
    assertEquals(GameResult.DRAW, game1.result());

    GameHeader game2 = index.getGameHeader(2);
    assertEquals(2820, game2.blackElo());

    GameHeader game4 = index.getGameHeader(4);
    assertEquals(4, game4.id());
  }

  @Test
  public void getSimpleGameFromInMemoryCopy() throws IOException {
    File cbh_only = ResourceLoader.materializeDatabaseStream(getClass(), "cbh_only");
    GameHeaderIndex index = GameHeaderIndex.open(cbh_only, DatabaseMode.IN_MEMORY, null);
    assertEquals(4, index.count());

    GameHeader game1 = index.getGameHeader(1);
    assertEquals(1, game1.id());
    assertEquals(0, game1.whitePlayerId());
    assertEquals(2200, game1.whiteElo());
    assertEquals(2, game1.round());
    assertEquals(1, game1.subRound());
    assertEquals(GameResult.DRAW, game1.result());

    GameHeader game2 = index.getGameHeader(2);
    assertEquals(2820, game2.blackElo());

    GameHeader game4 = index.getGameHeader(4);
    assertEquals(4, game4.id());
  }

  @Test(expected = IllegalArgumentException.class)
  public void getInvalidGame() throws IOException {
    File cbh_only = ResourceLoader.materializeDatabaseStream(getClass(), "cbh_only");
    GameHeaderIndex index = GameHeaderIndex.open(cbh_only, null);
    index.getGameHeader(5);
  }

  @Test(expected = IllegalArgumentException.class)
  public void getInvalidGameInMemory() throws IOException {
    File cbh_only = ResourceLoader.materializeDatabaseStream(getClass(), "cbh_only");
    GameHeaderIndex index = GameHeaderIndex.open(cbh_only, DatabaseMode.IN_MEMORY, null);
    index.getGameHeader(5);
  }

  private ImmutableGameHeader emptyGame() {
    return ImmutableGameHeader.builder()
        .movesOffset(0)
        .whitePlayerId(0)
        .blackPlayerId(0)
        .tournamentId(0)
        .annotatorId(0)
        .sourceId(0)
        .noMoves(0)
        .build();
  }

  @Test
  public void testAddGameHeaderToFileIndex() throws IOException {
    GameHeaderIndex index = GameHeaderIndex.open(gameHeaderFile, null);
    int oldSize = index.count();

    GameHeader newGame = emptyGame().withWhiteElo(2100);
    int id = index.add(newGame);
    assertEquals(oldSize + 1, id);
    assertEquals(oldSize + 1, index.count());
    GameHeader gameHeader = index.getGameHeader(id);
    assertEquals(id, gameHeader.id());
    assertEquals(2100, gameHeader.whiteElo());

    assertEquals(oldSize + 2, index.add(emptyGame()));
    assertEquals(oldSize + 2, index.count());

    index.close();
  }

  @Test
  public void testAddGameHeaderToInMemoryIndex() throws IOException {
    GameHeaderIndex index = GameHeaderIndex.open(gameHeaderFile, DatabaseMode.IN_MEMORY, null);
    int oldSize = index.count();

    GameHeader newGame = emptyGame().withWhiteElo(2100);
    int id = index.add(newGame);
    assertEquals(oldSize + 1, id);
    assertEquals(oldSize + 1, index.count());
    GameHeader gameHeader = index.getGameHeader(id);
    assertEquals(id, gameHeader.id());
    assertEquals(2100, gameHeader.whiteElo());

    assertEquals(oldSize + 2, index.add(emptyGame()));
    assertEquals(oldSize + 2, index.count());

    index.close();
  }

  @Test
  public void testUpdateGameHeader() throws IOException {
    GameHeaderIndex index = GameHeaderIndex.open(gameHeaderFile, DatabaseMode.IN_MEMORY, null);
    int oldSize = index.count();

    GameHeader oldGame = index.getGameHeader(3);
    assertEquals(4, oldGame.whitePlayerId());
    assertEquals(1, oldGame.blackPlayerId());

    GameHeader newGame = emptyGame().withWhitePlayerId(2).withBlackPlayerId(3);
    index.update(3, newGame);

    newGame = index.getGameHeader(3);
    assertEquals(2, newGame.whitePlayerId());
    assertEquals(3, newGame.blackPlayerId());
    assertEquals(oldSize, index.count());

    index.close();
  }

  @Test
  public void testGetAll() throws IOException {
    GameHeaderIndex index = GameHeaderIndex.open(gameHeaderFile, DatabaseMode.IN_MEMORY, null);
    List<GameHeader> all = index.getAll();
    assertEquals(19, all.size());
    assertEquals(index.getGameHeader(1), all.get(0));
    assertEquals(index.getGameHeader(15), all.get(14));
  }

  @Test
  public void testGetFilteredSerialized() throws IOException {
    File manyHeaderFile =
        ResourceLoader.materializeDatabaseStream(
            GameHeaderIndex.class, "many_headers", List.of(".cbh"));
    GameHeaderIndex headerIndex = GameHeaderIndex.open(manyHeaderFile, null);
    // Create an iterator that will use the raw level filter to pick a handful of headers
    // The sparsity of this will cause some batches in the internal iterator to be empty
    List<Integer> lookupIds = Arrays.asList(1024, 5191, 5192, 5195, 5823, 9015);

    List<GameHeader> matchingHeaders =
        headerIndex.getRange(
            1,
            headerIndex.count() + 1,
            new ItemStorageFilter<>() {
              @Override
              public boolean matches(int id, @NotNull GameHeader gameHeader) {
                // Send everything through, we're testing the serialized filter here
                return true;
              }

              @Override
              public boolean matchesSerialized(int id, @NotNull ByteBuffer buf) {
                int actualId =
                    ByteBufferUtil.getUnsigned24BitB(
                        buf.slice(buf.position() + 9, 3)); // id of white player offset
                assertEquals(actualId, id);
                return lookupIds.contains(id);
              }
            });

    assertEquals(headerIndex.count(), matchingHeaders.size());

    List<Integer> found =
        matchingHeaders.stream()
            .filter(Objects::nonNull)
            .map(GameHeader::id)
            .collect(Collectors.toList());
    assertEquals(lookupIds, found);
  }

  @Test
  public void testGetFiltered() throws IOException {
    File manyHeaderFile =
        ResourceLoader.materializeDatabaseStream(
            GameHeaderIndex.class, "many_headers", List.of(".cbh"));
    GameHeaderIndex headerIndex = GameHeaderIndex.open(manyHeaderFile, null);
    // Create an iterator that will use the raw level filter to pick a handful of headers
    // The sparsity of this will cause some batches in the internal iterator to be empty
    List<Integer> lookupIds = Arrays.asList(1024, 5191, 5192, 5195, 5823, 9015);

    List<GameHeader> matchingHeaders =
        headerIndex.getRange(
            1, headerIndex.count() + 1, (id, gameHeader) -> lookupIds.contains(id));

    assertEquals(headerIndex.count(), matchingHeaders.size());

    List<Integer> found =
        matchingHeaders.stream()
            .filter(Objects::nonNull)
            .map(GameHeader::id)
            .collect(Collectors.toList());
    assertEquals(lookupIds, found);
  }
}

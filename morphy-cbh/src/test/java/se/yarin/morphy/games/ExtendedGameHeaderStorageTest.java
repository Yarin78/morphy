package se.yarin.morphy.games;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import se.yarin.morphy.DatabaseMode;
import se.yarin.morphy.ResourceLoader;
import se.yarin.morphy.entities.Nation;
import se.yarin.morphy.util.CBUtil;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.junit.Assert.*;

public class ExtendedGameHeaderStorageTest {

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  @Test
  public void createEmpty() throws IOException {
    File file = folder.newFile("newstorage.cbj");
    file.delete();
    ExtendedGameHeaderStorage storage = ExtendedGameHeaderStorage.create(file, null);
    assertEquals(0, storage.count());
  }

  @Test
  public void createEmptyInMemory() {
    ExtendedGameHeaderStorage storage = new ExtendedGameHeaderStorage();
    assertEquals(0, storage.count());
  }

  @Test
  public void getItem() throws IOException {
    File cbh_cbj =
        ResourceLoader.materializeDatabaseStream(getClass(), "cbh_cbj_test", List.of(".cbj"));
    ExtendedGameHeaderStorage index = ExtendedGameHeaderStorage.open(cbh_cbj, null);
    assertEquals(4, index.count());

    ExtendedGameHeader game1 = index.get(1);
    assertEquals(0, game1.gameTagId());

    ExtendedGameHeader game2 = index.get(2);
    assertEquals(1, game2.gameTagId());

    ExtendedGameHeader game4 = index.get(4);
    assertEquals(2, game4.whiteTeamId());
    assertEquals(Nation.SWEDEN, game4.whiteRatingType().nation());
  }

  @Test
  public void getItemInMemory() throws IOException {
    File cbh_cbj =
        ResourceLoader.materializeDatabaseStream(getClass(), "cbh_cbj_test", List.of(".cbj"));
    ExtendedGameHeaderStorage index =
        ExtendedGameHeaderStorage.open(cbh_cbj, DatabaseMode.IN_MEMORY, null);
    assertEquals(4, index.count());

    ExtendedGameHeader game1 = index.get(1);
    assertEquals(0, game1.gameTagId());

    ExtendedGameHeader game2 = index.get(2);
    assertEquals(1, game2.gameTagId());

    ExtendedGameHeader game4 = index.get(4);
    assertEquals(2, game4.whiteTeamId());
    assertEquals(Nation.SWEDEN, game4.whiteRatingType().nation());
  }

  @Test(expected = IllegalArgumentException.class)
  public void getInvalidGame() throws IOException {
    File cbh_cbj =
        ResourceLoader.materializeDatabaseStream(getClass(), "cbh_cbj_test", List.of(".cbj"));
    ExtendedGameHeaderStorage index = ExtendedGameHeaderStorage.open(cbh_cbj, null);
    index.get(5);
  }

  @Test(expected = IllegalArgumentException.class)
  public void getInvalidGameInMemory() throws IOException {
    File cbh_cbj =
        ResourceLoader.materializeDatabaseStream(getClass(), "cbh_cbj_test", List.of(".cbj"));
    ExtendedGameHeaderStorage index =
        ExtendedGameHeaderStorage.open(cbh_cbj, DatabaseMode.IN_MEMORY, null);
    index.get(5);
  }

  @Test
  public void putItemInMemory() {
    ExtendedGameHeaderStorage storage = new ExtendedGameHeaderStorage();

    storage.put(1, ExtendedGameHeader.empty(1, 2).withGameTagId(2).withWhiteTeamId(3));

    ExtendedGameHeader item = storage.get(1);
    assertEquals(1, item.annotationOffset());
    assertEquals(2, item.movesOffset());
    assertEquals(2, item.gameTagId());
    assertEquals(3, item.whiteTeamId());
    assertEquals(-1, item.blackTeamId());
  }

  @Test
  public void putItem() throws IOException {
    File file = folder.newFile("newstorage.cbj");
    file.delete();
    ExtendedGameHeaderStorage storage = ExtendedGameHeaderStorage.create(file, null);

    storage.put(1, ExtendedGameHeader.empty(1, 2).withGameTagId(2).withWhiteTeamId(3));

    ExtendedGameHeader item = storage.get(1);
    assertEquals(1, item.annotationOffset());
    assertEquals(2, item.movesOffset());
    assertEquals(2, item.gameTagId());
    assertEquals(3, item.whiteTeamId());
    assertEquals(-1, item.blackTeamId());
  }

  @Test
  public void upgradeMissingExtendedStorage() throws IOException {
    File cbh = ResourceLoader.materializeDatabaseStream(getClass(), "cbh_only");
    File cbj = CBUtil.fileWithExtension(cbh, ".cbj");
    assertFalse(cbj.exists());

    ExtendedGameHeaderStorage.upgrade(cbh);
    assertTrue(cbj.exists());

    GameHeaderIndex index = GameHeaderIndex.open(cbh, null);
    ExtendedGameHeaderStorage extendedStorage = ExtendedGameHeaderStorage.open(cbj, null);
    assertEquals(index.count(), extendedStorage.count());
    assertTrue(index.count() > 0);
    for (int i = 1; i <= index.count(); i++) {
      GameHeader gameHeader = index.getGameHeader(i);
      ExtendedGameHeader extendedGameHeader = extendedStorage.get(i);
      assertEquals(gameHeader.annotationOffset(), extendedGameHeader.annotationOffset());
      assertEquals(gameHeader.movesOffset(), extendedGameHeader.movesOffset());
      assertEquals(-1, extendedGameHeader.gameTagId());
    }
  }

  @Test
  public void upgradeOldVersionOfExtendedStorage() throws IOException {
    File cbh = ResourceLoader.materializeDatabaseStream(getClass(), "hedgehog_old");
    File cbj = CBUtil.fileWithExtension(cbh, ".cbj");
    assertTrue(cbj.exists());
    ExtendedGameHeaderStorage.ExtProlog extProlog = ExtendedGameHeaderStorage.peekProlog(cbj);
    assertEquals(8, extProlog.version());

    ExtendedGameHeaderStorage.upgrade(cbh);

    ExtendedGameHeaderStorage extendedStorage = ExtendedGameHeaderStorage.open(cbj, null);
    assertEquals(
        ExtendedGameHeaderStorage.ExtProlog.DEFAULT_VERSION, extendedStorage.getStorageVersion());

    ExtendedGameHeader game16 = extendedStorage.get(16);
    assertEquals(2, game16.whiteTeamId());
    assertEquals(-1, game16.gameTagId()); // Not in this version of cbj, so default value
  }

  @Test
  public void upgradeShortenedExtendedStorage() throws IOException {
    File cbh = ResourceLoader.materializeDatabaseStream(getClass(), "shorter_cbj_test");
    File cbj = CBUtil.fileWithExtension(cbh, ".cbj");
    assertTrue(cbj.exists());

    ExtendedGameHeaderStorage.ExtProlog extProlog = ExtendedGameHeaderStorage.peekProlog(cbj);
    assertEquals(3, extProlog.numHeaders());
    ExtendedGameHeaderStorage.upgrade(cbh);

    extProlog = ExtendedGameHeaderStorage.peekProlog(cbj);
    assertEquals(4, extProlog.numHeaders());
  }
}

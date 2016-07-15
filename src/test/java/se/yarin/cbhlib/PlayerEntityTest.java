package se.yarin.cbhlib;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import se.yarin.cbhlib.entities.EntityStorageException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.junit.Assert.*;

public class PlayerEntityTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private File playerIndexFile;

    @Before
    public void setupEntityTest() throws IOException {
        playerIndexFile = materializeStream(this.getClass().getResourceAsStream("entity_test.cbp"));
    }

    private File materializeStream(InputStream stream) throws IOException {
        File file = folder.newFile("temp.cbp");
        FileOutputStream fos = new FileOutputStream(file);
        byte[] buf = new byte[0x1000];
        while (true) {
            int r = stream.read(buf);
            if (r == -1) {
                break;
            }
            fos.write(buf, 0, r);
        }
        fos.close();
        return file;
    }

    @Test
    public void testPlayerBaseStatistics() throws IOException {
        PlayerBase playerBase = PlayerBase.open(playerIndexFile);
        assertEquals(265, playerBase.getCount());
    }

    @Test
    public void testGetPlayerById() throws IOException {
        PlayerBase playerBase = PlayerBase.open(playerIndexFile);

        PlayerEntity p0 = playerBase.get(0);
        assertEquals(new PlayerEntity("Adams", "Michael"), p0);
        assertEquals(28, p0.getCount());
        assertEquals(2, p0.getFirstGameId());

        PlayerEntity p5 = playerBase.get(5);
        assertEquals(new PlayerEntity("Giri", "Anish"), p5);
        assertEquals(22, p5.getCount());
        assertEquals(6, p5.getFirstGameId());
    }


    @Test(expected = IOException.class)
    public void testGetPlayerAfterClosingDatabase() throws IOException {
        PlayerBase playerBase = PlayerBase.open(playerIndexFile);

        PlayerEntity p0 = playerBase.get(0);
        assertEquals(new PlayerEntity("Adams", "Michael"), p0);
        playerBase.close();

        playerBase.get(5);
    }

    @Test
    public void testGetInvalidPlayerById() throws IOException {
        PlayerBase playerBase = PlayerBase.open(playerIndexFile);
        assertNull(playerBase.get(1000000));
        assertNull(playerBase.get(-5));
    }

    @Test
    public void testGetAllPlayers() throws IOException {
        PlayerBase playerBase = PlayerBase.open(playerIndexFile);
        List<PlayerEntity> players = playerBase.getAll();
        assertEquals(playerBase.getCount(), players.size());
        for (PlayerEntity player : players) {
            assertNotNull(player);
        }
    }

    @Test
    public void testGetPlayerByKey() throws IOException {
        PlayerBase playerBase = PlayerBase.open(playerIndexFile);
        PlayerEntity player = playerBase.get(new PlayerEntity("Carlsen", "Magnus"));
        assertEquals(22, player.getCount());
    }

    @Test
    public void testGetMissingPlayerByKey() throws IOException {
        PlayerBase playerBase = PlayerBase.open(playerIndexFile);
        PlayerEntity player = playerBase.get(new PlayerEntity("Mardell", "Jimmy"));
        assertNull(player);
    }

    @Test
    public void testGetAscendingRangeOfPlayers() throws IOException {
        PlayerBase playerBase = PlayerBase.open(playerIndexFile);
        List<PlayerEntity> list = playerBase.getAscendingList(new PlayerEntity("M", ""), 3);
        assertEquals(3, list.size());
        assertEquals(new PlayerEntity("Machelett", "Heiko"), list.get(0));
        assertEquals(new PlayerEntity("Mainka", "Romuald"), list.get(1));
        assertEquals(new PlayerEntity("Maiwald", "Jens Uwe"), list.get(2));
    }

    @Test
    public void testGetDescendingRangeOfPlayers() throws IOException {
        PlayerBase playerBase = PlayerBase.open(playerIndexFile);
        List<PlayerEntity> list = playerBase.getDescendingList(new PlayerEntity("Sp", ""), 3);
        assertEquals(3, list.size());
        assertEquals(new PlayerEntity("Socko", "Bartosz"), list.get(0));
        assertEquals(new PlayerEntity("So", "Wesley"), list.get(1));
        assertEquals(new PlayerEntity("Smerdon", "David"), list.get(2));
    }

    @Test
    public void testGetFirstPlayer() throws IOException {
        PlayerBase playerBase = PlayerBase.open(playerIndexFile);
        assertEquals(new PlayerEntity("Adams", "Michael"), playerBase.getFirst());
    }

    @Test
    public void testGetLastPlayer() throws IOException {
        PlayerBase playerBase = PlayerBase.open(playerIndexFile);
        assertEquals(new PlayerEntity("Zwanzger", "Johannes"), playerBase.getLast());
    }

    @Test
    public void testGetNextPlayer() throws IOException {
        PlayerBase playerBase = PlayerBase.open(playerIndexFile);
        assertEquals(new PlayerEntity("Agopov", "Mikael"), playerBase.getNext(playerBase.getFirst()));
        assertNull(playerBase.getNext(playerBase.getLast()));
    }

    @Test
    public void testGetPreviousPlayer() throws IOException {
        PlayerBase playerBase = PlayerBase.open(playerIndexFile);
        assertEquals(new PlayerEntity("Zumsande", "Martin"), playerBase.getPrevious(playerBase.getLast()));
        assertNull(playerBase.getPrevious(playerBase.getFirst()));
    }

    @Test
    public void testStreamAllPlayers() throws IOException {
        PlayerBase playerBase = PlayerBase.open(playerIndexFile);

        assertEquals(playerBase.getCount(), playerBase.getAscendingStream().count());
        assertEquals(playerBase.getCount(), playerBase.getDescendingStream().count());
    }

    @Test
    public void testAddPlayer() throws IOException, EntityStorageException {
        PlayerBase playerBase = PlayerBase.open(playerIndexFile);
        int oldCount = playerBase.getCount();

        PlayerEntity newPlayer = PlayerEntity.builder()
            .lastName("Mardell")
            .firstName("Jimmy")
            .count(10)
            .firstGameId(7)
            .build();
        assertTrue(playerBase.streamAll().noneMatch(e -> e.equals(newPlayer)));

        PlayerEntity entity = playerBase.add(newPlayer);

        assertEquals(oldCount + 1, playerBase.getCount());
        assertTrue(entity.getId() >= 0);

        assertTrue(playerBase.streamAll().anyMatch(e -> e.equals(newPlayer)));
    }

    @Test
    public void testAddPlayerWithTooLongName() throws IOException, EntityStorageException {
        PlayerBase playerBase = PlayerBase.open(playerIndexFile);
        PlayerEntity newPlayer = new PlayerEntity("Thisisaverylongnamethatwillgettruncated", "");
        PlayerEntity entity = playerBase.add(newPlayer);
        assertEquals("Thisisaverylongnamethatwillget", entity.getLastName());
    }

    @Test
    public void testRenamePlayer() throws IOException, EntityStorageException {
        PlayerBase playerBase = PlayerBase.open(playerIndexFile);
        PlayerEntity player = playerBase.get(new PlayerEntity("Carlsen", "Magnus"));

        assertNotEquals(player.getId(), playerBase.getLast().getId());

        player = player.toBuilder().lastName("Zzz").build();
        playerBase.put(player.getId(), player);

        assertNull(playerBase.get(new PlayerEntity("Carlsen", "Magnus")));
        assertEquals(new PlayerEntity("Zzz", "Magnus"), playerBase.get(player.getId()));
        assertEquals(player.getId(), playerBase.getLast().getId());
    }

    @Test
    public void testChangePlayerStats() throws IOException, EntityStorageException {
        PlayerBase playerBase = PlayerBase.open(playerIndexFile);

        PlayerEntity player = playerBase.get(new PlayerEntity("Carlsen", "Magnus"));
        int id = player.getId();
        assertNotEquals(1, player.getCount());

        player = player.toBuilder().count(1).build();
        playerBase.put(player.getId(), player);

        player = playerBase.get(new PlayerEntity("Carlsen", "Magnus"));
        assertEquals(1, player.getCount());

        player = playerBase.get(id);
        assertEquals(1, player.getCount());
    }

    @Test
    public void testDeletePlayer() throws IOException, EntityStorageException {
        PlayerBase playerBase = PlayerBase.open(playerIndexFile);
        int oldCount = playerBase.getCount();

        PlayerEntity player = playerBase.get(new PlayerEntity("Carlsen", "Magnus"));
        assertTrue(playerBase.streamAll().anyMatch(e -> e.equals(player)));

        assertTrue(playerBase.delete(player.getId()));

        assertEquals(oldCount - 1, playerBase.getCount());
        assertTrue(playerBase.streamAll().noneMatch(e -> e.equals(player)));
    }

    @Test
    public void testDeleteMissingPlayer() throws IOException, EntityStorageException {
        PlayerBase playerBase = PlayerBase.open(playerIndexFile);
        int oldCount = playerBase.getCount();

        assertTrue(playerBase.delete(100));
        assertEquals(oldCount - 1, playerBase.getCount());

        // Delete same player again
        assertFalse(playerBase.delete(100));
        assertEquals(oldCount - 1, playerBase.getCount());
    }

    @Test
    public void testDeleteMultiplePlayersAndThenAddNewPlayer() throws IOException, EntityStorageException {
        // This should reuse player ids
        // Not sure that's the correct thing to test here though since it's an implementation detail?

        PlayerBase playerBase = PlayerBase.open(playerIndexFile);
        playerBase.delete(10);
        playerBase.delete(30);
        playerBase.delete(57);

        PlayerEntity first = playerBase.add(new PlayerEntity("First", ""));
        assertEquals(57, first.getId());
        PlayerEntity second = playerBase.add(new PlayerEntity("Second", ""));
        assertEquals(30, second.getId());
        PlayerEntity third = playerBase.add(new PlayerEntity("Third", ""));
        assertEquals(10, third.getId());
        PlayerEntity fourth = playerBase.add(new PlayerEntity("Fourth", ""));
        assertEquals(playerBase.getCount() - 1, fourth.getId());
    }

    @Test
    public void testInMemoryStorage() throws IOException {
        PlayerBase playerBase = PlayerBase.openInMemory(playerIndexFile);
        assertEquals(playerBase.getCount(), playerBase.getAll().size());
    }

    @Test
    public void testCreatePlayerBase() throws IOException {
        File file = folder.newFile("newbase.cbp");
        file.delete(); // Need to delete it first so we can create it in PlayerBase
        PlayerBase playerBase = PlayerBase.create(file);
        assertEquals(0, playerBase.getCount());
    }

    @Test
    public void testOpenCreatedPlayerBase() throws IOException, EntityStorageException {
        File file = folder.newFile("newbase.cbp");
        file.delete(); // Need to delete it first so we can create it in PlayerBase

        PlayerBase playerBase = PlayerBase.create(file);
        playerBase.add(new PlayerEntity("Carlsen", "Magnus"));
        playerBase.add(new PlayerEntity("Karjakin", "Sergey"));
        playerBase.add(new PlayerEntity("Svidler", "Peter"));
        playerBase.close();

        playerBase = PlayerBase.open(file);
        PlayerEntity player = playerBase.get(1);
        assertEquals(new PlayerEntity("Karjakin", "Sergey"), player);
    }
}

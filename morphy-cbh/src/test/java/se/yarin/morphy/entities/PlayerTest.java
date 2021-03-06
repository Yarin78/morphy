package se.yarin.morphy.entities;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import se.yarin.morphy.ResourceLoader;
import se.yarin.morphy.exceptions.MorphyIOException;
import se.yarin.morphy.games.GameIndex;
import se.yarin.morphy.storage.OpenOption;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class PlayerTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private File playerIndexFile;

    @Before
    public void setupEntityTest() throws IOException {
        playerIndexFile = ResourceLoader.materializeStream(
                "entity_test",
                GameIndex.class.getResourceAsStream("entity_test.cbp"),
                ".cbp");
    }

    @Test
    public void testPlayerBaseStatistics() throws IOException {
        PlayerIndex playerIndex = PlayerIndex.open(playerIndexFile);
        assertEquals(265, playerIndex.count());
    }

    @Test
    public void testGetPlayerById() throws IOException {
        PlayerIndex playerIndex = PlayerIndex.open(playerIndexFile);

        Player p0 = playerIndex.get(0);
        assertEquals(Player.of("Adams", "Michael"), p0);
        assertEquals(28, p0.count());
        assertEquals(2, p0.firstGameId());

        Player p5 = playerIndex.get(5);
        assertEquals(Player.of("Giri", "Anish"), p5);
        assertEquals(22, p5.count());
        assertEquals(6, p5.firstGameId());
    }


    @Test(expected = MorphyIOException.class)
    public void testGetPlayerAfterClosingIndex() throws IOException {
        PlayerIndex playerIndex = PlayerIndex.open(playerIndexFile);

        Player p0 = playerIndex.get(0);
        assertEquals(Player.of("Adams", "Michael"), p0);
        playerIndex.close();

        playerIndex.get(5);
    }

    @Test
    public void testGetInvalidPlayerByIdInSafeMode() throws IOException {
        PlayerIndex playerIndex = PlayerIndex.open(playerIndexFile, OpenOption.READ);
        assertEquals("", playerIndex.get(1000000).getFullName());
    }

    @Test(expected = MorphyIOException.class)
    public void testGetInvalidPlayerByIdInStrictMode() throws IOException {
        PlayerIndex playerIndex = PlayerIndex.open(playerIndexFile, OpenOption.READ, OpenOption.STRICT);
        playerIndex.get(1000000);
    }

    @Test
    public void testGetAllPlayers() throws IOException {
        PlayerIndex playerIndex = PlayerIndex.open(playerIndexFile);
        List<Player> players = playerIndex.getAll();
        assertEquals(playerIndex.count(), players.size());
        for (Player player : players) {
            assertNotNull(player);
        }
    }

    @Test
    public void testGetPlayerByKey() throws IOException {
        PlayerIndex playerIndex = PlayerIndex.open(playerIndexFile);
        Player player = playerIndex.get(Player.of("Carlsen", "Magnus"));
        assertEquals(22, player.count());
    }

    @Test
    public void testGetMissingPlayerByKey() throws IOException {
        PlayerIndex playerIndex = PlayerIndex.open(playerIndexFile);
        Player player = playerIndex.get(Player.of("foo", "bar"));
        assertNull(player);
    }

    @Test
    public void testGetAscendingRangeOfPlayers() throws IOException {
        PlayerIndex playerIndex = PlayerIndex.open(playerIndexFile);
        List<Player> list = playerIndex.streamOrderedAscending(Player.of("M", ""))
                .limit(3)
                .collect(Collectors.toList());
        assertEquals(3, list.size());
        assertEquals(Player.of("Machelett", "Heiko"), list.get(0));
        assertEquals(Player.of("Mainka", "Romuald"), list.get(1));
        assertEquals(Player.of("Maiwald", "Jens Uwe"), list.get(2));
    }

    @Test
    public void testGetDescendingRangeOfPlayers() throws IOException {
        PlayerIndex playerIndex = PlayerIndex.open(playerIndexFile);
        List<Player> list = playerIndex.streamOrderedDescending(Player.of("Sp", ""))
                .limit(3)
                .collect(Collectors.toList());
        assertEquals(3, list.size());
        assertEquals(Player.of("Socko", "Bartosz"), list.get(0));
        assertEquals(Player.of("So", "Wesley"), list.get(1));
        assertEquals(Player.of("Smerdon", "David"), list.get(2));
    }

    @Test
    public void testGetFirstPlayer() throws IOException {
        PlayerIndex playerIndex = PlayerIndex.open(playerIndexFile);
        assertEquals(Player.of("Adams", "Michael"), playerIndex.getFirst());
    }

    @Test
    public void testGetLastPlayer() throws IOException {
        PlayerIndex playerIndex = PlayerIndex.open(playerIndexFile);
        assertEquals(Player.of("Zwanzger", "Johannes"), playerIndex.getLast());
    }

    @Test
    public void testGetNextPlayer() throws IOException {
        PlayerIndex playerIndex = PlayerIndex.open(playerIndexFile);
        assertEquals(Player.of("Agopov", "Mikael"), playerIndex.getNext(playerIndex.getFirst()));
        assertNull(playerIndex.getNext(playerIndex.getLast()));
    }

    @Test
    public void testGetPreviousPlayer() throws IOException {
        PlayerIndex playerIndex = PlayerIndex.open(playerIndexFile);
        assertEquals(Player.of("Zumsande", "Martin"), playerIndex.getPrevious(playerIndex.getLast()));
        assertNull(playerIndex.getPrevious(playerIndex.getFirst()));
    }

    @Test
    public void testStreamAllPlayers() throws IOException {
        PlayerIndex playerIndex = PlayerIndex.open(playerIndexFile);

        assertEquals(playerIndex.count(), playerIndex.streamOrderedAscending().count());
        assertEquals(playerIndex.count(), playerIndex.streamOrderedDescending().count());
    }
    /*
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
        assertTrue(playerBase.stream().noneMatch(e -> e.equals(newPlayer)));

        PlayerEntity entity = playerBase.add(newPlayer);

        assertEquals(oldCount + 1, playerBase.getCount());
        assertTrue(entity.getId() >= 0);

        assertTrue(playerBase.stream().anyMatch(e -> e.equals(newPlayer)));
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
        assertTrue(playerBase.stream().anyMatch(e -> e.equals(player)));

        assertTrue(playerBase.delete(player.getId()));

        assertEquals(oldCount - 1, playerBase.getCount());
        assertTrue(playerBase.stream().noneMatch(e -> e.equals(player)));
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

    @Test
    public void testPlayerSortingOrder() {
        // Tested in ChessBase 16
        String[] playerNames = new String[] {
                "",
                "A",
                "B",
                "Blo",
                "Blö",
                "Jimmy",
                "Zoo",
                "ceasar",
                "moo",
                "tjo",
                "¤",
                "Åland",
                "É",
                "Öland",
                "Û"
        };

        for (int i = 0; i < playerNames.length - 1; i++) {
            PlayerEntity p1 = new PlayerEntity(playerNames[i], "");
            PlayerEntity p2 = new PlayerEntity(playerNames[i+1], "");
            assertTrue(String.format("Expected '%s' < '%s'", playerNames[i], playerNames[i+1]), p1.compareTo(p2) < 0);
        }
    }

     */
}

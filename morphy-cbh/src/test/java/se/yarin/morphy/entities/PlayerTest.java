package se.yarin.morphy.entities;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import se.yarin.morphy.DatabaseMode;
import se.yarin.morphy.ResourceLoader;
import se.yarin.morphy.exceptions.MorphyIOException;
import se.yarin.morphy.exceptions.MorphyNotSupportedException;
import se.yarin.morphy.games.GameIndex;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class PlayerTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private File playerIndexFile;
    private File playerIndexSmallHeaderFile;

    @Before
    public void setupEntityTest() throws IOException {
        playerIndexFile = ResourceLoader.materializeStream(
                "entity_test",
                GameIndex.class.getResourceAsStream("entity_test.cbp"),
                ".cbp");
        playerIndexSmallHeaderFile = ResourceLoader.materializeStream(
                "small_header",
                GameIndex.class.getResourceAsStream("small_header.cbp"),
                ".cbp");
    }

    @Test
    public void testPlayerBaseStatistics() throws IOException {
        PlayerIndex playerIndex = PlayerIndex.open(playerIndexFile);
        assertEquals(32, playerIndex.storageHeader().headerSize());
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
        PlayerIndex playerIndex = PlayerIndex.open(playerIndexFile, DatabaseMode.READ_REPAIR);
        assertEquals("", playerIndex.get(1000000).getFullName());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetInvalidPlayerByIdInStrictMode() throws IOException {
        PlayerIndex playerIndex = PlayerIndex.open(playerIndexFile, DatabaseMode.READ_ONLY);
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
    public void testGetAscendingRangeOfPlayersWithExclusiveEnd() throws IOException {
        PlayerIndex playerIndex = PlayerIndex.open(playerIndexFile);
        List<Player> list = playerIndex.streamOrderedAscending(
                Player.of("Machelett", "Heiko"),
                Player.of("Maiwald", "Jens Uwe"))
                .collect(Collectors.toList());
        assertEquals(2, list.size());
        assertEquals(Player.of("Machelett", "Heiko"), list.get(0));
        assertEquals(Player.of("Mainka", "Romuald"), list.get(1));
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
    public void testPrefixSearchLastName() throws IOException {
        PlayerIndex playerIndex = PlayerIndex.open(playerIndexFile);
        List<Player> list = playerIndex.prefixSearch("Carl").collect(Collectors.toList());
        assertEquals(2, list.size());
        assertEquals(Player.of("Carlsen", "Magnus"), list.get(0));
        assertEquals(Player.of("Carlstedt", "Jonathan"), list.get(1));
    }

    @Test
    public void testPrefixSearchFullName() throws IOException {
        PlayerIndex playerIndex = PlayerIndex.open(playerIndexFile);
        List<Player> list = playerIndex.prefixSearch("Walter", "Stefan" ).collect(Collectors.toList());
        assertEquals(2, list.size());
        assertEquals(Player.of("Walter", "Stefan1"), list.get(0));
        assertEquals(Player.of("Walter", "Stefan2"), list.get(1));
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

    @Test
    public void testAddPlayer() throws IOException {
        PlayerIndex playerIndex = PlayerIndex.open(playerIndexFile);
        int oldCount = playerIndex.count();

        Player newPlayer = ImmutablePlayer.builder()
                .lastName("Mardell")
                .firstName("Jimmy")
                .count(10)
                .firstGameId(7)
                .build();
        assertTrue(playerIndex.stream().noneMatch(e -> e.equals(newPlayer)));

        Player entity = playerIndex.get(playerIndex.add(newPlayer));

        assertEquals(oldCount + 1, playerIndex.count());
        assertTrue(entity.id() >= 0);

        assertTrue(playerIndex.stream().anyMatch(e -> e.equals(newPlayer)));
    }

    @Test
    public void testAddPlayerWithTooLongName() throws IOException {
        PlayerIndex playerIndex = PlayerIndex.open(playerIndexFile);
        Player newPlayer = Player.of("Thisisaverylongnamethatwillgettruncated", "");
        Player entity = playerIndex.get(playerIndex.add(newPlayer));
        assertEquals("Thisisaverylongnamethatwillget", entity.lastName());
    }

    @Test
    public void testRenamePlayer() throws IOException {
        PlayerIndex playerIndex = PlayerIndex.open(playerIndexFile);
        Player player = playerIndex.get(Player.of("Carlsen", "Magnus"));

        assertNotEquals(player.id(), playerIndex.getLast().id());

        player = ImmutablePlayer.copyOf(player).withLastName("Zzz");
        playerIndex.put(player.id(), player);

        assertNull(playerIndex.get(Player.of("Carlsen", "Magnus")));
        assertEquals(Player.of("Zzz", "Magnus"), playerIndex.get(player.id()));
        assertEquals(player.id(), playerIndex.getLast().id());
    }

    @Test
    public void testChangePlayerStats() throws IOException {
        PlayerIndex playerIndex = PlayerIndex.open(playerIndexFile);

        Player player = playerIndex.get(Player.of("Carlsen", "Magnus"));
        int id = player.id();
        assertNotEquals(1, player.count());

        player = ImmutablePlayer.copyOf(player).withCount(1);
        playerIndex.put(player.id(), player);

        player = playerIndex.get(Player.of("Carlsen", "Magnus"));
        assertEquals(1, player.count());

        player = playerIndex.get(id);
        assertEquals(1, player.count());
    }

    @Test
    public void testDeletePlayer() throws IOException {
        PlayerIndex playerIndex = PlayerIndex.open(playerIndexFile);
        int oldCount = playerIndex.count();

        Player player = playerIndex.get(Player.of("Carlsen", "Magnus"));
        assertTrue(playerIndex.stream().anyMatch(e -> e.equals(player)));

        assertTrue(playerIndex.delete(player.id()));

        assertEquals(oldCount - 1, playerIndex.count());
        assertTrue(playerIndex.stream().noneMatch(e -> e.equals(player)));
    }

    @Test
    public void testDeleteMissingPlayer() throws IOException {
        PlayerIndex playerIndex = PlayerIndex.open(playerIndexFile);
        int oldCount = playerIndex.count();

        assertTrue(playerIndex.delete(100));
        assertEquals(oldCount - 1, playerIndex.count());

        // Delete same player again
        assertFalse(playerIndex.delete(100));
        assertEquals(oldCount - 1, playerIndex.count());
    }

    @Test
    public void testDeleteMultiplePlayersAndThenAddNewPlayer() throws IOException {
        // This should reuse player ids
        // Not sure that's the correct thing to test here though since it's an implementation detail?

        PlayerIndex playerIndex = PlayerIndex.open(playerIndexFile);
        playerIndex.delete(10);
        playerIndex.delete(30);
        playerIndex.delete(57);

        int firstId = playerIndex.add(Player.of("First", ""));
        assertEquals(57, firstId);
        int secondId = playerIndex.add(Player.of("Second", ""));
        assertEquals(30, secondId);
        int thirdId = playerIndex.add(Player.of("Third", ""));
        assertEquals(10, thirdId);
        int fourthId = playerIndex.add(Player.of("Fourth", ""));
        assertEquals(playerIndex.count() - 1, fourthId);
    }

    @Test
    public void testOpenIndexWithSmallHeader() throws IOException {
        PlayerIndex playerIndex = PlayerIndex.open(playerIndexSmallHeaderFile, DatabaseMode.READ_ONLY);
        assertEquals(28, playerIndex.storageHeader().headerSize());
        assertEquals(169, playerIndex.count());
        Player player = playerIndex.get(5);
        assertEquals("Geller, Efim", player.getFullName());
    }

    @Test(expected = MorphyNotSupportedException.class)
    public void testOpenIndexWithSmallHeaderForWrite() throws IOException {
        PlayerIndex.open(playerIndexSmallHeaderFile);
    }

    @Test
    public void testInMemoryStorage() throws IOException {
        PlayerIndex playerIndex = PlayerIndex.open(playerIndexFile, DatabaseMode.IN_MEMORY);
        assertEquals(playerIndex.count(), playerIndex.getAll().size());
    }

    @Test
    public void testCreatePlayerIndex() throws IOException {
        File file = folder.newFile("newbase.cbp");
        file.delete(); // Need to delete it first so we can create it in PlayerBase
        PlayerIndex playerIndex = PlayerIndex.create(file);
        assertEquals(0, playerIndex.count());
    }

    @Test
    public void testOpenCreatedPlayerIndex() throws IOException {
        File file = folder.newFile("newbase.cbp");
        file.delete(); // Need to delete it first so we can create it in PlayerBase

        PlayerIndex playerIndex = PlayerIndex.create(file);
        playerIndex.add(Player.of("Carlsen", "Magnus"));
        playerIndex.add(Player.of("Karjakin", "Sergey"));
        playerIndex.add(Player.of("Svidler", "Peter"));
        playerIndex.close();

        playerIndex = PlayerIndex.open(file);
        Player player = playerIndex.get(1);
        assertEquals(Player.of("Karjakin", "Sergey"), player);
    }

    @Test
    public void upgradeIndexWithSmallHeader() throws IOException {
        long oldLength = playerIndexSmallHeaderFile.length();
        PlayerIndex playerIndex = PlayerIndex.open(playerIndexSmallHeaderFile, DatabaseMode.READ_ONLY);
        assertEquals(28, playerIndex.storageHeader().headerSize());
        assertEquals(169, playerIndex.count());
        assertEquals("Geller, Efim", playerIndex.get(5).getFullName());
        playerIndex.close();

        PlayerIndex.upgrade(playerIndexSmallHeaderFile);

        assertEquals(4 + oldLength, playerIndexSmallHeaderFile.length());
        playerIndex = PlayerIndex.open(playerIndexSmallHeaderFile, DatabaseMode.READ_ONLY);
        assertEquals(32, playerIndex.storageHeader().headerSize());
        assertEquals(169, playerIndex.count());
        assertEquals("Geller, Efim", playerIndex.get(5).getFullName());
        playerIndex.close();
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
            Player p1 = Player.of(playerNames[i], "");
            Player p2 = Player.of(playerNames[i+1], "");
            assertTrue(String.format("Expected '%s' < '%s'", playerNames[i], playerNames[i+1]), p1.compareTo(p2) < 0);
        }
    }

}

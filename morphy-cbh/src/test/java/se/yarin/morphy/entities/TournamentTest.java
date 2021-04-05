package se.yarin.morphy.entities;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import se.yarin.chess.Date;
import se.yarin.morphy.Database;
import se.yarin.morphy.DatabaseMode;
import se.yarin.morphy.ResourceLoader;
import se.yarin.morphy.exceptions.MorphyInvalidDataException;
import se.yarin.morphy.games.GameIndex;
import se.yarin.morphy.util.CBUtil;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class TournamentTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private File tournamentIndexFile;
    private File tournamentExtraFile;

    @Before
    public void setupEntityTest() throws IOException {
        tournamentIndexFile = ResourceLoader.materializeDatabaseStream(
                Database.class, "database/World-ch", "World-ch", List.of(".cbt", ".cbtt"));
        tournamentExtraFile = CBUtil.fileWithExtension(tournamentIndexFile, ".cbtt");
    }

    @Test
    public void testTournamentIndexStatistics() throws IOException, MorphyInvalidDataException {
        TournamentIndex tournamentIndex = TournamentIndex.open(tournamentIndexFile);
        assertEquals(52, tournamentIndex.count());
    }

    @Test
    public void testGetTournamentById() throws IOException, MorphyInvalidDataException {
        TournamentIndex tournamentIndex = TournamentIndex.open(tournamentIndexFile);
        TournamentExtraStorage tournamentExtraStorage = TournamentExtraStorage.open(tournamentExtraFile);

        Tournament t41 = tournamentIndex.get(41);
        assertEquals("World-ch Tournament", t41.title());
        assertEquals(58, t41.count());
        assertEquals(21, t41.category());
        assertEquals(14, t41.rounds());
        assertEquals(new Date(2007, 9, 13), t41.date());
        assertEquals(Nation.MEXICO, t41.nation());
        assertEquals("Mexico City", t41.place());
        assertEquals(TournamentTimeControl.NORMAL, t41.timeControl());
        assertEquals(TournamentType.ROUND_ROBIN, t41.type());
        assertTrue(t41.complete());
        assertFalse(t41.boardPoints());
        assertFalse(t41.teamTournament());
        assertFalse(t41.threePointsWin());

        TournamentExtra extra = tournamentExtraStorage.get(41);
        assertEquals(19.4242, extra.latitude(), 0.001);
        assertEquals(-99.1589, extra.longitude(), 0.001);
    }

    @Test
    public void testGetTournamentByIdInMemoryMode() throws IOException, MorphyInvalidDataException {
        TournamentIndex tournamentIndex = TournamentIndex.open(tournamentIndexFile, DatabaseMode.IN_MEMORY);
        TournamentExtraStorage tournamentExtraStorage = TournamentExtraStorage.open(tournamentExtraFile, DatabaseMode.IN_MEMORY);
        Tournament t41 = tournamentIndex.get(41);
        TournamentExtra extra = tournamentExtraStorage.get(41);
        assertEquals("World-ch Tournament", t41.title());
        assertEquals(19.4242, extra.latitude(), 0.001);
    }

    @Test
    public void testGetTournamentByKey() throws IOException, MorphyInvalidDataException {
        TournamentIndex tournamentIndex = TournamentIndex.open(tournamentIndexFile);
        Tournament key = Tournament.of("World-ch Carlsen-Caruana", "London", new Date(2018, 11, 9));
        Tournament tournament = tournamentIndex.get(key);
        assertEquals(50, tournament.id());
        assertEquals(12, tournament.count());
    }

    @Test
    public void testTournamentSerialization() {
        Tournament newTournament = ImmutableTournament.builder()
                .title("My tournament")
                .date(new Date(2016, 7, 10))
                .category(15)
                .rounds(7)
                .type(TournamentType.KNOCK_OUT)
                .complete(false)
                .threePointsWin(true)
                .teamTournament(false)
                .boardPoints(true)
                .timeControl(TournamentTimeControl.CORRESPONDENCE)
                .place("my place")
                .nation(Nation.ARGENTINA)
                .build();

        TournamentIndex tournamentIndex = new TournamentIndex();

        ByteBuffer buf = ByteBuffer.allocate(1000);
        tournamentIndex.serialize(newTournament, buf);
        buf.flip();

        Tournament tournament = tournamentIndex.deserialize(1, 3, 100, buf.array());

        assertEquals("My tournament", tournament.title());
        assertEquals(new Date(2016, 7, 10), tournament.date());
        assertEquals(15, tournament.category());
        assertEquals(7, tournament.rounds());
        assertEquals(TournamentType.KNOCK_OUT, tournament.type());
        assertFalse(tournament.complete());
        assertTrue(tournament.threePointsWin());
        assertFalse(tournament.teamTournament());
        assertTrue(tournament.boardPoints());
        assertEquals(TournamentTimeControl.CORRESPONDENCE, tournament.timeControl());
        assertEquals("my place", tournament.place());
        assertEquals(Nation.ARGENTINA, tournament.nation());
        assertEquals(3, tournament.count());
        assertEquals(100, tournament.firstGameId());
    }

    @Test
    public void testPrefixSearch() throws IOException {
        TournamentIndex tournamentIndex = TournamentIndex.open(tournamentIndexFile);
        List<Tournament> list = tournamentIndex.prefixSearch(1951, "World-ch").collect(Collectors.toList());
        assertEquals(1, list.size());
        assertEquals("World-ch18 Botvinnik-Bronstein +5-5=14", list.get(0).title());
    }

    @Test
    public void testRangeSearch() throws IOException {
        TournamentIndex tournamentIndex = TournamentIndex.open(tournamentIndexFile);
        List<Tournament> list = tournamentIndex.rangeSearch(1951, 1958).collect(Collectors.toList());
        assertEquals(4, list.size());
        assertEquals("World-ch21 Botvinnik-Symslov +7-5=11", list.get(0).title());
        assertEquals("World-ch20 Smyslov-Botvinnik +6-3=13", list.get(1).title());
        assertEquals("World-ch19 Botvinnik-Symslov +7-7=10", list.get(2).title());
        assertEquals("World-ch18 Botvinnik-Bronstein +5-5=14", list.get(3).title());
    }

    @Test
    public void testTournamentSortingOrder() {
        // Tested in ChessBase 16
        String[] tournamentTitles = {
                "¤",
                "Ärgh",
                "Åre",
                "öde",
                "",
                "ABC",
                "Foo",
                "Zoo",
                "tjoss",
        };

        for (int i = 0; i < tournamentTitles.length - 1; i++) {
            Tournament t1 = Tournament.of(tournamentTitles[i], Date.unset());
            Tournament t2 = Tournament.of(tournamentTitles[i+1], Date.unset());
            assertTrue(String.format("Expected '%s' < '%s'", tournamentTitles[i], tournamentTitles[i+1]), t1.compareTo(t2) < 0);
        }

        Tournament[] tournaments = {
                Tournament.of("foo", new Date(1980)),
                Tournament.of("tjoss", new Date(1980)),
                Tournament.of("abc", new Date(1975)),
                Tournament.of("def", new Date(1975)),
                Tournament.of("foo", Date.unset()),
        };

        for (int i = 0; i < tournaments.length - 1; i++) {
            assertTrue(String.format("Expected '%s' < '%s'", tournaments[i], tournaments[i+1]), tournaments[i].compareTo(tournaments[i+1]) < 0);
        }
    }
}

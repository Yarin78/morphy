package se.yarin.morphy.entities;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import se.yarin.cbhlib.entities.Nation;
import se.yarin.cbhlib.entities.TournamentTimeControl;
import se.yarin.cbhlib.entities.TournamentType;
import se.yarin.cbhlib.util.CBUtil;
import se.yarin.chess.Date;
import se.yarin.morphy.ResourceLoader;
import se.yarin.morphy.exceptions.MorphyInvalidDataException;
import se.yarin.morphy.games.GameIndex;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;

import static java.nio.file.StandardOpenOption.READ;
import static org.junit.Assert.*;

public class TournamentTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private File tournamentIndexFile;
    private File tournamentExtraFile;

    @Before
    public void setupEntityTest() throws IOException {
        Path worldChPath = Files.createTempDirectory("worldch");

        tournamentIndexFile = ResourceLoader.materializeDatabaseStream(
                GameIndex.class,
                "World-ch/World-ch",
                worldChPath.toFile(),
                "World-ch", new String[] { ".cbt", ".cbtt"});

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
        assertEquals(19.4242, t41.extra().latitude(), 0.001);
        assertEquals(-99.1589, t41.extra().longitude(), 0.001);
    }

    @Test
    public void testGetTournamentByIdWithoutExtra() throws IOException, MorphyInvalidDataException {
        tournamentExtraFile.delete();
        TournamentIndex tournamentIndex = TournamentIndex.open(tournamentIndexFile);

        Tournament t41 = tournamentIndex.get(41);
        assertEquals("World-ch Tournament", t41.title());
        assertEquals(0.0, t41.extra().latitude(), 0.001);
    }

    @Test
    public void testGetTournamentByIdWithoutExtraReadMode() throws IOException, MorphyInvalidDataException {
        tournamentExtraFile.delete();
        TournamentIndex tournamentIndex = TournamentIndex.open(tournamentIndexFile, Set.of(READ), true);

        Tournament t41 = tournamentIndex.get(41);
        assertEquals("World-ch Tournament", t41.title());
        assertEquals(0.0, t41.extra().latitude(), 0.001);
    }

    @Test
    public void testGetTournamentByKey() throws IOException, MorphyInvalidDataException {
        TournamentIndex tournamentIndex = TournamentIndex.open(tournamentIndexFile);
        Tournament key = Tournament.of("World-ch Carlsen-Caruana", "London", new Date(2018, 11, 9));
        Tournament tournament = tournamentIndex.get(key);
        assertEquals(50, tournament.id());
        assertEquals(12, tournament.count());
        assertEquals(53.2046, tournament.extra().latitude(), 1e-6);
    }

    // Since Tournament has an extra file outside the default EntityIndex,
    // we need explicit test that it's updated correctly on changes

    private Tournament testTournament(int startDay, int endDay) {
        Date startDate = new Date(2021, 3, startDay);
        Date endDate = new Date(2021, 3, endDay);
        return ImmutableTournament.builder()
                .title("foobar")
                .date(startDate)
                .extra(ImmutableTournamentExtra.builder()
                        .endDate(endDate)
                        .build())
                .build();
    }

    @Test
    public void testAddTournament() {
        TournamentIndex tournamentIndex = new TournamentIndex();
        Tournament newTournament = testTournament(1, 5);
        int addedTournamentId = tournamentIndex.add(newTournament);
        assertEquals(0, addedTournamentId);
        Tournament addedTournament = tournamentIndex.get(addedTournamentId);
        assertEquals(newTournament.date(), addedTournament.date());
        assertEquals(newTournament.extra().endDate(), addedTournament.extra().endDate());
        assertEquals(1, tournamentIndex.count());
        assertEquals(1, tournamentIndex.extraStorage().numEntries());
    }

    @Test
    public void testPutTournamentById() {
        TournamentIndex tournamentIndex = new TournamentIndex();
        int id = tournamentIndex.add(testTournament(1, 5));
        assertEquals(0, id);

        Tournament putTournament = testTournament(1, 6);  // Update with same primary key
        tournamentIndex.put(0, putTournament);
        Tournament updatedTournament = tournamentIndex.get(id);
        assertEquals(updatedTournament.date(), putTournament.date());
        assertEquals(updatedTournament.extra().endDate(), putTournament.extra().endDate());

        putTournament = testTournament(2, 7);  // Update with different primary key
        tournamentIndex.put(0, putTournament);
        updatedTournament = tournamentIndex.get(id);
        assertEquals(updatedTournament.date(), putTournament.date());
        assertEquals(updatedTournament.extra().endDate(), putTournament.extra().endDate());
    }

    @Test
    public void testPutTournamentByKey() {
        TournamentIndex tournamentIndex = new TournamentIndex();
        int id = tournamentIndex.add(testTournament(1, 5));
        assertEquals(0, id);
        Tournament putTournament = testTournament(1, 6);  // Same start date needed
        int updatedId = tournamentIndex.put(putTournament);
        assertEquals(id, updatedId);
        Tournament updatedTournament = tournamentIndex.get(id);
        assertEquals(updatedTournament.date(), putTournament.date());
        assertEquals(updatedTournament.extra().endDate(), putTournament.extra().endDate());
    }

    @Test
    public void testDeleteTournamentById() {
        TournamentIndex tournamentIndex = new TournamentIndex();
        int id = tournamentIndex.add(testTournament(1, 5));
        assertEquals(0, id);
        tournamentIndex.delete(0);
        Tournament deletedTournament = tournamentIndex.get(id);
        assertNull(deletedTournament);
    }

    @Test
    public void testDeleteTournamentByKey() {
        TournamentIndex tournamentIndex = new TournamentIndex();
        int id = tournamentIndex.add(testTournament(1, 5));
        assertEquals(0, id);
        tournamentIndex.delete(testTournament(1, 6));  // The difference in extra date doesn't matter
        Tournament deletedTournament = tournamentIndex.get(id);
        assertNull(deletedTournament);
    }

    @Test
    public void testAddTournamentWithoutExtraData() {
        TournamentIndex tournamentIndex = new TournamentIndex();
        tournamentIndex.add(Tournament.of("a", new Date(2021, 3, 1)));
        tournamentIndex.add(Tournament.of("b", new Date(2021, 3, 1)));

        assertEquals(2, tournamentIndex.count());
        assertEquals(0, tournamentIndex.extraStorage().numEntries());

        tournamentIndex.add(testTournament(1, 2));

        assertEquals(3, tournamentIndex.count());
        assertEquals(3, tournamentIndex.extraStorage().numEntries());
    }

    @Test
    public void testRemoveExtraDataFromTournament() {
        TournamentIndex tournamentIndex = new TournamentIndex();
        tournamentIndex.add(Tournament.of("a", new Date(2021, 3, 1)));
        tournamentIndex.add(Tournament.of("b", new Date(2021, 3, 1)));
        Tournament tt = testTournament(1, 2);
        tournamentIndex.add(tt);

        assertEquals(3, tournamentIndex.count());
        assertEquals(3, tournamentIndex.extraStorage().numEntries());

        assertNotEquals(TournamentExtra.empty(), tournamentIndex.get(2).extra());

        int id = tournamentIndex.put(ImmutableTournament.copyOf(tt).withExtra(TournamentExtra.empty()));
        assertEquals(2, id);

        assertEquals(TournamentExtra.empty(), tournamentIndex.get(id).extra());
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
                .extra(ImmutableTournamentExtra.builder()
                        .latitude(1.2)
                        .longitude(3.4)
                        .endDate(new Date(2016, 7, 13))
                        .tiebreakRules(Arrays.asList(TiebreakRule.RR_NUM_WINS, TiebreakRule.RR_POINT_GROUP))
                        .build()
                )
                .build();

        TournamentIndex tournamentIndex = new TournamentIndex();
        TournamentExtraStorage extraSerializer = new TournamentExtraStorage();

        ByteBuffer buf = ByteBuffer.allocate(1000);
        ByteBuffer bufExtra = ByteBuffer.allocate(1000);
        tournamentIndex.serialize(newTournament, buf);
        extraSerializer.serializeItem(newTournament.extra(), bufExtra);
        buf.flip();
        bufExtra.flip();

        Tournament tournament = tournamentIndex.deserialize(1, 3, 100, buf.array(), extraSerializer.deserializeItem(0, bufExtra));

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

        assertEquals(1.2, tournament.extra().latitude(), 1e-6);
        assertEquals(3.4, tournament.extra().longitude(), 1e-6);
        assertEquals(new Date(2016, 7, 13), tournament.extra().endDate());
        assertEquals(2, tournament.extra().tiebreakRules().size());
        assertEquals(TiebreakRule.RR_NUM_WINS, tournament.extra().tiebreakRules().get(0));
        assertEquals(TiebreakRule.RR_POINT_GROUP, tournament.extra().tiebreakRules().get(1));
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

package se.yarin.morphy.entities;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import se.yarin.cbhlib.entities.Nation;
import se.yarin.cbhlib.entities.TournamentTimeControl;
import se.yarin.cbhlib.entities.TournamentType;
import se.yarin.chess.Date;
import se.yarin.morphy.ResourceLoader;
import se.yarin.morphy.exceptions.MorphyInvalidDataException;
import se.yarin.morphy.games.GameIndex;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import static org.junit.Assert.*;

public class TournamentTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private File tournamentIndexFile;
    private File tournamentExtraFile;

    @Before
    public void setupEntityTest() throws IOException {
        tournamentIndexFile = ResourceLoader.materializeStream(
                "worldch",
                GameIndex.class.getResourceAsStream("World-ch/World-ch.cbt"),
                ".cbt");
        tournamentExtraFile = ResourceLoader.materializeStream(
                "worldch",
                GameIndex.class.getResourceAsStream("World-ch/World-ch.cbtt"),
                ".cbtt");
    }


    @Test
    public void testTournamentIndexStatistics() throws IOException, MorphyInvalidDataException {
        TournamentIndex tournamentIndex = TournamentIndex.open(tournamentIndexFile, tournamentExtraFile);
        assertEquals(52, tournamentIndex.count());
    }

    @Test
    public void testGetTournamentById() throws IOException, MorphyInvalidDataException {
        TournamentIndex tournamentIndex = TournamentIndex.open(tournamentIndexFile, tournamentExtraFile);

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
        File missingFile = new File(tournamentExtraFile.getPath() + "_missing");
        TournamentIndex tournamentIndex = TournamentIndex.open(tournamentIndexFile, missingFile);

        Tournament t41 = tournamentIndex.get(41);
        assertEquals("World-ch Tournament", t41.title());
        assertEquals(0.0, t41.extra().latitude(), 0.001);
    }

    @Test
    public void testGetTournamentByKey() throws IOException, MorphyInvalidDataException {
        TournamentIndex tournamentIndex = TournamentIndex.open(tournamentIndexFile, tournamentExtraFile);
        Tournament key = Tournament.of("World-ch Carlsen-Caruana", "London", new Date(2018, 11, 9));
        Tournament tournament = tournamentIndex.get(key);
        assertEquals(50, tournament.id());
        assertEquals(12, tournament.count());
        assertEquals(53.2046, tournament.extra().latitude(), 1e-6);
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
        TournamentExtraSerializer extraSerializer = new TournamentExtraSerializer();

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

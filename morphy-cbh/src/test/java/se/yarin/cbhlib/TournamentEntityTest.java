package se.yarin.cbhlib;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import se.yarin.cbhlib.entities.EntityStorageException;
import se.yarin.chess.Date;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.Assert.*;

public class TournamentEntityTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private File tournamentIndexFile;

    @Before
    public void setupEntityTest() throws IOException {
        tournamentIndexFile = materializeStream(this.getClass().getResourceAsStream("entity_test.cbt"));
    }

    private File materializeStream(InputStream stream) throws IOException {
        File file = folder.newFile("temp.cbt");
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
    public void testTournamentBaseStatistics() throws IOException {
        TournamentBase tournamentBase = TournamentBase.open(tournamentIndexFile);
        assertEquals(3, tournamentBase.getCount());
    }

    @Test
    public void testGetTournamentById() throws IOException {
        TournamentBase tournamentBase = TournamentBase.open(tournamentIndexFile);

        TournamentEntity t0 = tournamentBase.get(0);
        assertEquals("Tata Steel-A 78th", t0.getTitle());
        assertEquals(92, t0.getCount());
        assertEquals(20, t0.getCategory());
        assertEquals(13, t0.getRounds());
        assertEquals(new Date(2016, 1, 16), t0.getDate());
        assertEquals(Nation.NETHERLANDS, t0.getNation());
        assertEquals("Wijk aan Zee", t0.getPlace());
        assertEquals(TournamentTimeControl.NORMAL, t0.getTimeControl());
        assertEquals(TournamentType.ROUND_ROBIN, t0.getType());
        assertTrue(t0.isComplete());
        assertFalse(t0.isBoardPoints());
        assertFalse(t0.isTeamTournament());
        assertFalse(t0.isThreePointsWin());

        TournamentEntity t2 = tournamentBase.get(2);
        assertEquals("Bundesliga 1516", t2.getTitle());
        assertEquals(921, t2.getCount());
        assertEquals(0, t2.getCategory());
        assertEquals(15, t2.getRounds());
        assertEquals(new Date(2015, 9, 18), t2.getDate());
        assertEquals(Nation.GERMANY, t2.getNation());
        assertEquals("Germany", t2.getPlace());
        assertEquals(TournamentTimeControl.NORMAL, t2.getTimeControl());
        assertEquals(TournamentType.ROUND_ROBIN, t2.getType());
        assertTrue(t2.isComplete());
        assertFalse(t2.isBoardPoints());
        assertTrue(t2.isTeamTournament());
        assertFalse(t2.isThreePointsWin());
    }

    @Test
    public void testGetTournamentByKey() throws IOException {
        TournamentBase tournamentBase = TournamentBase.open(tournamentIndexFile);
        TournamentEntity tournament = tournamentBase.get(new TournamentEntity("London Classic 7th"));
        assertEquals("London", tournament.getPlace());
    }

    @Test
    public void testTournamentSerialization() throws IOException, EntityStorageException {
        TournamentBase tournamentBase = TournamentBase.open(tournamentIndexFile);

        TournamentEntity newTournament = TournamentEntity.builder()
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
                .count(3)
                .firstGameId(100)
                .build();

        TournamentEntity entity = tournamentBase.add(newTournament);

        assertTrue(entity.getId() >= 0);

        TournamentEntity tournament = tournamentBase.get(entity.getId());

        // Must be different reference since structure is mutable
        assertNotSame(newTournament, tournament);

        assertEquals("My tournament", tournament.getTitle());
        assertEquals(new Date(2016, 7, 10), tournament.getDate());
        assertEquals(15, tournament.getCategory());
        assertEquals(7, tournament.getRounds());
        assertEquals(TournamentType.KNOCK_OUT, tournament.getType());
        assertFalse(tournament.isComplete());
        assertTrue(tournament.isThreePointsWin());
        assertFalse(tournament.isTeamTournament());
        assertTrue(tournament.isBoardPoints());
        assertEquals(TournamentTimeControl.CORRESPONDENCE, tournament.getTimeControl());
        assertEquals("my place", tournament.getPlace());
        assertEquals(Nation.ARGENTINA, tournament.getNation());
        assertEquals(3, tournament.getCount());
        assertEquals(100, tournament.getFirstGameId());
    }
}

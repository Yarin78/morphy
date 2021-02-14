package se.yarin.cbhlib.entities;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import se.yarin.cbhlib.entities.*;
import se.yarin.cbhlib.storage.EntityStorageDuplicateKeyException;
import se.yarin.cbhlib.storage.EntityStorageException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.*;

public class TeamEntityTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private File teamIndexFile;

    @Before
    public void setupEntityTest() throws IOException {
        teamIndexFile = materializeStream(this.getClass().getResourceAsStream("entity_test.cbe"));
    }

    private File materializeStream(InputStream stream) throws IOException {
        File file = folder.newFile("temp.cbe");
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
    public void testTeamBaseStatistics() throws IOException {
        TeamBase teamBase = TeamBase.open(teamIndexFile);
        assertEquals(17, teamBase.getCount());
    }

    @Test
    public void testGetTeamById() throws IOException {
        TeamBase teamBase = TeamBase.open(teamIndexFile);

        TeamEntity s1 = teamBase.get(1);
        assertEquals("Baden-Baden", s1.getTitle());
        assertEquals(0, s1.getTeamNumber());
        assertTrue(s1.isSeason());
        assertEquals(2015, s1.getYear());
        assertEquals(Nation.GERMANY, s1.getNation());
        assertEquals(120, s1.getCount());
    }

    @Test
    public void testGetTeamByKey() throws IOException, EntityStorageDuplicateKeyException {
        TeamBase teamBase = TeamBase.open(teamIndexFile);
        TeamEntity key = new TeamEntity("Hamburger SK", 0, true, 2015, Nation.GERMANY);
        TeamEntity team = teamBase.get(key);
        assertEquals(4, team.getId());
    }

    @Test
    public void testTeamSerialization() throws IOException, EntityStorageException {
        TeamBase teamBase = TeamBase.open(teamIndexFile);

        TeamEntity newTeam = TeamEntity.builder()
                .title("My team")
                .teamNumber(7)
                .season(false)
                .year(2001)
                .nation(Nation.ALGERIA)
                .count(15)
                .firstGameId(123)
                .build();

        TeamEntity entity = teamBase.add(newTeam);

        assertTrue(entity.getId() >= 0);

        TeamEntity team = teamBase.get(entity.getId());

        // Must be different reference since structure is mutable
        assertNotSame(newTeam, team);

        assertEquals("My team", team.getTitle());
        assertEquals(7, team.getTeamNumber());
        assertFalse(team.isSeason());
        assertEquals(2001, team.getYear());
        assertEquals(Nation.ALGERIA, team.getNation());
        assertEquals(15, team.getCount());
        assertEquals(123, team.getFirstGameId());
    }

    @Test
    public void testTeamSortingOrder() {
        // Tested in ChessBase 16
        String[] teamNames = {
                "123",
                "Bar",
                "ar",
                "moo",
                "åäö",
                "èbasd",
        };

        for (int i = 0; i < teamNames.length - 1; i++) {
            TeamEntity t1 = new TeamEntity(teamNames[i]);
            TeamEntity t2 = new TeamEntity(teamNames[i + 1]);
            assertTrue(String.format("Expected '%s' < '%s'", teamNames[i], teamNames[i + 1]), t1.compareTo(t2) < 0);
        }
    }
}

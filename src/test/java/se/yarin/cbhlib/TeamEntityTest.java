package se.yarin.cbhlib;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

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
        assertEquals(Nation.NAT_53, s1.getNation());
        assertEquals(120, s1.getCount());
    }

    @Test
    public void testGetTeamByKey() throws IOException {
        TeamBase teamBase = TeamBase.open(teamIndexFile);
        TeamEntity team = teamBase.get(new TeamEntity("Hamburger SK"));
        assertEquals(2015, team.getYear());
        assertEquals(Nation.NAT_53, team.getNation());
    }

    @Test
    public void testTeamSerialization() throws IOException, EntityStorageException {
        TeamBase teamBase = TeamBase.open(teamIndexFile);

        TeamEntity newTeam = new TeamEntity("My team");
        newTeam.setTeamNumber(7);
        newTeam.setSeason(false);
        newTeam.setYear(2001);
        newTeam.setNation(Nation.NAT_3);
        newTeam.setCount(15);
        newTeam.setFirstGameId(123);

        TeamEntity entity = teamBase.add(newTeam);

        assertTrue(entity.getId() >= 0);

        TeamEntity team = teamBase.get(entity.getId());

        // Must be different reference since structure is mutable
        assertNotSame(newTeam, team);

        assertEquals("My team", team.getTitle());
        assertEquals(7, team.getTeamNumber());
        assertFalse(team.isSeason());
        assertEquals(2001, team.getYear());
        assertEquals(Nation.NAT_3, team.getNation());
        assertEquals(15, team.getCount());
        assertEquals(123, team.getFirstGameId());
    }
}
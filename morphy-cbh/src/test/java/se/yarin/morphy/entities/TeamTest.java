package se.yarin.morphy.entities;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import se.yarin.morphy.ResourceLoader;
import se.yarin.morphy.games.GameHeaderIndex;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TeamTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private File teamIndexFile;

    @Before
    public void setupEntityTest() throws IOException {
        teamIndexFile = ResourceLoader.materializeStream(
                "entity_test",
                GameHeaderIndex.class.getResourceAsStream("entity_test.cbe"),
                ".cbe");
    }

    @Test
    public void testTeamBaseStatistics() throws IOException {
        TeamIndex teamIndex = TeamIndex.open(teamIndexFile, null);
        assertEquals(17, teamIndex.count());
    }

    @Test
    public void testGetTeamById() throws IOException {
        TeamIndex teamIndex = TeamIndex.open(teamIndexFile, null);

        Team s1 = teamIndex.get(1);
        assertEquals("Baden-Baden", s1.title());
        assertEquals(0, s1.teamNumber());
        assertTrue(s1.season());
        assertEquals(2015, s1.year());
        assertEquals(Nation.GERMANY, s1.nation());
        assertEquals(120, s1.count());
    }

    @Test
    public void testGetTeamByKey() throws IOException {
        TeamIndex teamIndex = TeamIndex.open(teamIndexFile, null);

        Team key = Team.of("Hamburger SK", 0, true, 2015, Nation.GERMANY);
        Team team = teamIndex.get(key);
        assertEquals(4, team.id());
    }

    @Test
    public void testTeamSerialization() {
        Team newTeam = ImmutableTeam.builder()
            .title("My team")
            .teamNumber(7)
            .season(false)
            .year(2001)
            .nation(Nation.ALGERIA)
            .build();

        TeamIndex teamIndex = new TeamIndex();
        ByteBuffer buf = ByteBuffer.allocate(1000);
        teamIndex.serialize(newTeam, buf);
        buf.flip();

        Team team = teamIndex.deserialize(1, 2, 3, buf.array());

        assertEquals("My team", team.title());
        assertEquals(7, team.teamNumber());
        assertFalse(team.season());
        assertEquals(2001, team.year());
        assertEquals(Nation.ALGERIA, team.nation());
        assertEquals(2, team.count());
        assertEquals(3, team.firstGameId());
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
            Team t1 = Team.of(teamNames[i]);
            Team t2 = Team.of(teamNames[i + 1]);
            assertTrue(String.format("Expected '%s' < '%s'", teamNames[i], teamNames[i + 1]), t1.compareTo(t2) < 0);
        }
    }
}

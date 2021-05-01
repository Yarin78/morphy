package se.yarin.morphy.entities;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import se.yarin.morphy.ResourceLoader;
import se.yarin.morphy.games.GameIndex;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class GameTagTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private File gameTagIndexFile;

    @Before
    public void setupEntityTest() throws IOException {
        gameTagIndexFile = ResourceLoader.materializeStream(
                "entity_test",
                GameIndex.class.getResourceAsStream("entity_test.cbl"),
                ".cbl");
    }

    @Test
    public void testGameTagBaseStatistics() throws IOException {
        GameTagIndex gameTagIndex = GameTagIndex.open(gameTagIndexFile, null);
        assertEquals(3, gameTagIndex.count());
    }

    @Test
    public void testGetGameTagById() throws IOException {
        GameTagIndex gameTagIndex = GameTagIndex.open(gameTagIndexFile, null);

        GameTag s1 = gameTagIndex.get(3);
        assertEquals("This is English", s1.englishTitle());
        assertEquals("This is German", s1.germanTitle());
        assertEquals("This is French", s1.frenchTitle());
        assertEquals("This is Spanish", s1.spanishTitle());
        assertEquals("This is Italian", s1.italianTitle());
        assertEquals("This is Dutch", s1.dutchTitle());
        assertEquals("This is Slovenia", s1.slovenianTitle());
        assertEquals("", s1.resTitle());
    }

    @Test
    public void testGetTeamByKey() throws IOException {
        GameTagIndex gameTagIndex = GameTagIndex.open(gameTagIndexFile, null);
        GameTag gameTag = gameTagIndex.prefixSearch("This is English").stream().findFirst().orElse(null);
        assertNotNull(gameTag);
        assertEquals(3, gameTag.id());
    }

    @Test
    public void testGameTagSerialization() {
        GameTag newGameTag = ImmutableGameTag.builder()
                .englishTitle("eng")
                .germanTitle("ger")
                .frenchTitle("fra")
                .spanishTitle("esp")
                .italianTitle("ita")
                .dutchTitle("hol")
                .slovenianTitle("slo")
                .resTitle("res2")
                .count(15)
                .firstGameId(123)
                .build();

        GameTagIndex gameTagIndex = new GameTagIndex();
        ByteBuffer buf = ByteBuffer.allocate(2000);
        gameTagIndex.serialize(newGameTag, buf);
        buf.flip();

        GameTag gameTag = gameTagIndex.deserialize(1, 2, 3, buf.array());

        assertEquals("eng", gameTag.englishTitle());
        assertEquals("ger", gameTag.germanTitle());
        assertEquals("fra", gameTag.frenchTitle());
        assertEquals("esp", gameTag.spanishTitle());
        assertEquals("ita", gameTag.italianTitle());
        assertEquals("hol", gameTag.dutchTitle());
        assertEquals("slo", gameTag.slovenianTitle());
        assertEquals("res2", gameTag.resTitle());
        assertEquals(2, gameTag.count());
        assertEquals(3, gameTag.firstGameId());
    }
}

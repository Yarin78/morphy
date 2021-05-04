package se.yarin.morphy.entities;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import se.yarin.chess.Date;
import se.yarin.morphy.Database;
import se.yarin.morphy.ResourceLoader;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SourceTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private File sourceIndexFile;

    @Before
    public void setupEntityTest() throws IOException {
        sourceIndexFile = ResourceLoader.materializeDatabaseStream(
                Database.class, "database/World-ch", "World-ch", List.of(".cbs"));
    }

    @Test
    public void testSourceBaseStatistics() throws IOException {
        SourceIndex sourceIndex = SourceIndex.open(sourceIndexFile, null);
        assertEquals(24, sourceIndex.count());
    }

    @Test
    public void testGetSourceById() throws IOException {
        SourceIndex sourceIndex = SourceIndex.open(sourceIndexFile, null);

        Source s1 = sourceIndex.get(1);
        assertEquals("100 Jahre Schach", s1.title());
        assertEquals("ChessBase", s1.publisher());
        assertEquals(new Date(2000, 4, 19), s1.publication());
        assertEquals(new Date(2000, 4, 19), s1.date());
        assertEquals(1, s1.version());
        assertEquals(SourceQuality.HIGH, s1.quality());
        assertEquals(20, s1.count());
    }

    @Test
    public void testGetSourceByKey() throws IOException {
        SourceIndex sourceIndex = SourceIndex.open(sourceIndexFile, null);
        Source source = sourceIndex.get(Source.of("CBM 127"));
        assertEquals(12, source.count());
    }

    @Test
    public void testSourceSerialization() {
        Source newSource = ImmutableSource.builder()
            .title("My source")
            .publisher("my publisher")
            .publication(new Date(2016, 7, 10))
            .date(new Date(2015, 1, 2))
            .version(3)
            .quality(SourceQuality.MEDIUM)
            .build();

        SourceIndex sourceIndex = new SourceIndex();
        ByteBuffer buf = ByteBuffer.allocate(1000);
        sourceIndex.serialize(newSource, buf);
        buf.flip();

        Source source = sourceIndex.deserialize(1, 2, 3, buf.array());

        assertEquals("My source", source.title());
        assertEquals("my publisher", source.publisher());
        assertEquals(new Date(2016, 7, 10), source.publication());
        assertEquals(new Date(2015, 1, 2), source.date());
        assertEquals(3, source.version());
        assertEquals(SourceQuality.MEDIUM, source.quality());
        assertEquals(2, source.count());
        assertEquals(3, source.firstGameId());
    }

    @Test
    public void testSourceSortingOrder() {
        // Tested in ChessBase 16
        String[] sourceTitles = {
                "Öhh",
                "äsch",
                "",
                "TE",
                "fooBar",
        };

        for (int i = 0; i < sourceTitles.length - 1; i++) {
            Source s1 = Source.of(sourceTitles[i]);
            Source s2 = Source.of(sourceTitles[i + 1]);
            assertTrue(String.format("Expected '%s' < '%s'", sourceTitles[i], sourceTitles[i + 1]), s1.compareTo(s2) < 0);
        }
    }
}

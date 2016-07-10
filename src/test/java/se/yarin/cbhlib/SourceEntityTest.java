package se.yarin.cbhlib;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import se.yarin.chess.Date;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

public class SourceEntityTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private File sourceIndexFile;

    @Before
    public void setupEntityTest() throws IOException {
        sourceIndexFile = materializeStream(this.getClass().getResourceAsStream("entity_test.cbs"));
    }

    private File materializeStream(InputStream stream) throws IOException {
        File file = folder.newFile("temp.cbs");
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
    public void testSourceBaseStatistics() throws IOException {
        SourceBase sourceBase = SourceBase.open(sourceIndexFile);
        assertEquals(5, sourceBase.getCount());
    }

    @Test
    public void testGetSourceById() throws IOException {
        SourceBase sourceBase = SourceBase.open(sourceIndexFile);

        SourceEntity s1 = sourceBase.get(1);
        assertEquals("CBM 170", s1.getTitle());
        assertEquals("ChessBase", s1.getPublisher());
        assertEquals(new Date(2016, 1, 15), s1.getPublication());
        assertEquals(new Date(2016, 1, 15), s1.getDate());
        assertEquals(1, s1.getVersion());
        assertEquals(SourceQuality.HIGH, s1.getQuality());
        assertEquals(190, s1.getCount());
    }

    @Test
    public void testGetSourceByKey() throws IOException {
        SourceBase sourceBase = SourceBase.open(sourceIndexFile);
        SourceEntity source = sourceBase.get(new SourceEntity("CBM 171"));
        assertEquals(2, source.getVersion());
    }

    @Test
    public void testSourceSerialization() throws IOException, EntityStorageException {
        SourceBase sourceBase = SourceBase.open(sourceIndexFile);

        SourceEntity newSource = new SourceEntity("My source");
        newSource.setPublisher("my publisher");
        newSource.setPublication(new Date(2016, 7, 10));
        newSource.setDate(new Date(2015, 1, 2));
        newSource.setVersion(3);
        newSource.setQuality(SourceQuality.MEDIUM);
        newSource.setCount(1);
        newSource.setFirstGameId(10);

        SourceEntity entity = sourceBase.add(newSource);

        assertTrue(entity.getId() >= 0);

        SourceEntity source = sourceBase.get(entity.getId());

        // Must be different reference since structure is mutable
        assertNotSame(newSource, source);

        assertEquals("My source", source.getTitle());
        assertEquals("my publisher", source.getPublisher());
        assertEquals(new Date(2016, 7, 10), source.getPublication());
        assertEquals(new Date(2015, 1, 2), source.getDate());
        assertEquals(3, source.getVersion());
        assertEquals(SourceQuality.MEDIUM, source.getQuality());
        assertEquals(1, source.getCount());
        assertEquals(10, source.getFirstGameId());
    }
}

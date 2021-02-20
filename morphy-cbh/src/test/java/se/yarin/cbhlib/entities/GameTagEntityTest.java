package se.yarin.cbhlib.entities;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import se.yarin.cbhlib.storage.EntityStorageDuplicateKeyException;
import se.yarin.cbhlib.storage.EntityStorageException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.Assert.*;

public class GameTagEntityTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private File gameTagIndexFile;

    @Before
    public void setupEntityTest() throws IOException {
        gameTagIndexFile = materializeStream(this.getClass().getResourceAsStream("entity_test.cbl"));
    }

    private File materializeStream(InputStream stream) throws IOException {
        File file = folder.newFile("temp.cbl");
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
    public void testGameTagBaseStatistics() throws IOException {
        GameTagBase gameTagBase = GameTagBase.open(gameTagIndexFile);
        assertEquals(3, gameTagBase.getCount());
    }

    @Test
    public void testGetGameTagById() throws IOException {
        GameTagBase gameTag = GameTagBase.open(gameTagIndexFile);

        GameTagEntity s1 = gameTag.get(3);
        assertEquals("This is English", s1.getEnglishTitle());
        assertEquals("This is German", s1.getGermanTitle());
        assertEquals("This is French", s1.getFrenchTitle());
        assertEquals("This is Spanish", s1.getSpanishTitle());
        assertEquals("This is Italian", s1.getItalianTitle());
        assertEquals("This is Dutch", s1.getDutchTitle());
        assertEquals("This is Slovenia", s1.getSlovenianTitle());
        assertEquals("", s1.getResTitle());
    }

    @Test
    public void testGetTeamByKey() throws IOException, EntityStorageDuplicateKeyException {
        GameTagBase gameTag = GameTagBase.open(gameTagIndexFile);
        assertEquals(3, gameTag.prefixSearch("").count());
    }

    @Test
    public void testGameTagSerialization() throws IOException, EntityStorageException {
        GameTagBase gameTagBase = GameTagBase.open(gameTagIndexFile);

        GameTagEntity newGameTag = GameTagEntity.builder()
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

        GameTagEntity entity = gameTagBase.add(newGameTag);

        assertTrue(entity.getId() >= 0);

        GameTagEntity gameTag = gameTagBase.get(entity.getId());

        // Must be different reference since structure is mutable
        assertNotSame(newGameTag, gameTag);

        assertEquals("eng", gameTag.getEnglishTitle());
        assertEquals("ger", gameTag.getGermanTitle());
        assertEquals("fra", gameTag.getFrenchTitle());
        assertEquals("esp", gameTag.getSpanishTitle());
        assertEquals("ita", gameTag.getItalianTitle());
        assertEquals("hol", gameTag.getDutchTitle());
        assertEquals("slo", gameTag.getSlovenianTitle());
        assertEquals("res2", gameTag.getResTitle());
        assertEquals(15, gameTag.getCount());
        assertEquals(123, gameTag.getFirstGameId());
    }
}

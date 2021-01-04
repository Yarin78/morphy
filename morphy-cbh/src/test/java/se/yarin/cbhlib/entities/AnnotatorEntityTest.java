package se.yarin.cbhlib.entities;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import se.yarin.cbhlib.entities.AnnotatorBase;
import se.yarin.cbhlib.entities.AnnotatorEntity;
import se.yarin.cbhlib.storage.EntityStorageDuplicateKeyException;
import se.yarin.cbhlib.storage.EntityStorageException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.Assert.*;

public class AnnotatorEntityTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private File annotatorIndexFile;

    @Before
    public void setupEntityTest() throws IOException {
        annotatorIndexFile = materializeStream(this.getClass().getResourceAsStream("entity_test.cbc"));
    }

    private File materializeStream(InputStream stream) throws IOException {
        File file = folder.newFile("temp.cbc");
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
    public void testAnnotatorBaseStatistics() throws IOException {
        AnnotatorBase annotatorBase = AnnotatorBase.open(annotatorIndexFile);
        assertEquals(27, annotatorBase.getCount());
    }

    @Test
    public void testGetAnnotatorById() throws IOException {
        AnnotatorBase annotatorBase = AnnotatorBase.open(annotatorIndexFile);

        AnnotatorEntity a1 = annotatorBase.get(1);
        assertEquals("Marin,M", a1.getName());
        assertEquals(9, a1.getCount());

        AnnotatorEntity a7 = annotatorBase.get(7);
        assertEquals("Krasenkow,K", a7.getName());
        assertEquals(5, a7.getCount());
    }

    @Test
    public void testGetAnnotatorByKey() throws IOException, EntityStorageDuplicateKeyException {
        AnnotatorBase annotatorBase = AnnotatorBase.open(annotatorIndexFile);
        AnnotatorEntity annotator = annotatorBase.get(new AnnotatorEntity("Marin,M"));
        assertEquals(9, annotator.getCount());
    }

    @Test
    public void testAnnotatorSerialization() throws IOException, EntityStorageException {
        AnnotatorBase annotatorBase = AnnotatorBase.open(annotatorIndexFile);

        AnnotatorEntity newAnnotator = AnnotatorEntity.builder()
            .name("My annotator")
            .count(2)
            .firstGameId(34)
            .build();

        AnnotatorEntity entity = annotatorBase.add(newAnnotator);

        assertTrue(entity.getId() >= 0);

        AnnotatorEntity annotator = annotatorBase.get(entity.getId());

        // Must be different reference since structure is mutable
        assertNotSame(newAnnotator, annotator);

        assertEquals("My annotator", annotator.getName());
        assertEquals(2, annotator.getCount());
        assertEquals(34, annotator.getFirstGameId());
    }

    @Test
    public void testAnnotatorSortingOrder() {
        // Tested in ChessBase 16
        String[] annotators = {
                "È",
                "ågren",
                "ûrgh",
                "",
                "Foo",
        };

        for (int i = 0; i < annotators.length - 1; i++) {
            AnnotatorEntity a1 = new AnnotatorEntity(annotators[i]);
            AnnotatorEntity a2 = new AnnotatorEntity(annotators[i + 1]);
            assertTrue(String.format("Expected '%s' < '%s'", annotators[i], annotators[i + 1]), a1.compareTo(a2) < 0);
        }
    }
}

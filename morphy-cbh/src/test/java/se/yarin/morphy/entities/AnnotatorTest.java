package se.yarin.morphy.entities;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import se.yarin.morphy.Database;
import se.yarin.morphy.ResourceLoader;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AnnotatorTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private File annotatorIndexFile;

    @Before
    public void setupEntityTest() throws IOException {
        annotatorIndexFile = ResourceLoader.materializeDatabaseStream(
                Database.class, "database/World-ch", "World-ch", List.of(".cbc"));
    }

    @Test
    public void testAnnotatorBaseStatistics() throws IOException {
        AnnotatorIndex annotatorIndex = AnnotatorIndex.open(annotatorIndexFile, null);
        assertEquals(82, annotatorIndex.count());
    }

    @Test
    public void testGetAnnotatorById() throws IOException {
        AnnotatorIndex annotatorIndex = AnnotatorIndex.open(annotatorIndexFile, null);

        Annotator a2 = annotatorIndex.get(2);
        assertEquals("Knaak,R", a2.name());
        assertEquals(9, a2.count());

        Annotator a7 = annotatorIndex.get(7);
        assertEquals("Nimzowitsch,A", a7.name());
        assertEquals(2, a7.count());
    }

    @Test
    public void testGetAnnotatorByKey() throws IOException {
        AnnotatorIndex annotatorIndex = AnnotatorIndex.open(annotatorIndexFile, null);
        Annotator annotator = annotatorIndex.get(Annotator.of("Stohl,I"));
        assertEquals(11, annotator.count());
    }

    @Test
    public void testAnnotatorSerialization() {
        Annotator newAnnotator = Annotator.of("My annotator");

        AnnotatorIndex annotatorIndex = new AnnotatorIndex();
        ByteBuffer buf = ByteBuffer.allocate(1000);
        annotatorIndex.serialize(newAnnotator, buf);
        buf.flip();

        Annotator annotator = annotatorIndex.deserialize(1, 2, 3, buf.array());

        assertEquals("My annotator", annotator.name());
        assertEquals(2, annotator.count());
        assertEquals(3, annotator.firstGameId());
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
            Annotator a1 = Annotator.of(annotators[i]);
            Annotator a2 = Annotator.of(annotators[i + 1]);
            assertTrue(String.format("Expected '%s' < '%s'", annotators[i], annotators[i + 1]), a1.compareTo(a2) < 0);
        }
    }
}

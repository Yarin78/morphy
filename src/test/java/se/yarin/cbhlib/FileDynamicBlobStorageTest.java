package se.yarin.cbhlib;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FileDynamicBlobStorageTest {
    private static final int CHUNK_SIZE = 4096;
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private DynamicBlobStorage createStorage() throws IOException {
        File file = folder.newFile();
        file.delete();
        FileDynamicBlobStorage.createEmptyStorage(file);
        return new FileDynamicBlobStorage(file, new StringBlobSizeRetriever(), CHUNK_SIZE, CHUNK_SIZE);
    }

    private ByteBuffer createBlob(String value) {
        ByteBuffer blob = ByteBuffer.allocate(value.length() + 4);
        blob.putInt(value.length() + 4);
        blob.put(value.getBytes(CBUtil.cbCharSet));
        blob.flip();
        return blob;
    }

    private String parseBlob(ByteBuffer blob) {
        int length = blob.getInt();
        assertEquals(length - 4, blob.remaining());
        return new String(blob.array(), 4, length - 4, CBUtil.cbCharSet);
    }

    private class StringBlobSizeRetriever implements BlobSizeRetriever {
        @Override
        public int getBlobSize(ByteBuffer buf) {
            return buf.getInt(buf.position());
        }
    }

    @Test
    public void testCreateStorage() throws IOException {
        createStorage();
    }

    @Test
    public void addAndRetrieveBlobToStorage() throws IOException {
        DynamicBlobStorage storage = createStorage();
        int ofs = storage.addBlob(createBlob("hello world"));
        assertEquals("hello world", parseBlob(storage.getBlob(ofs)));
    }

    @Test
    public void addMultipleBlobs() throws IOException {
        DynamicBlobStorage storage = createStorage();
        assertEquals(26, storage.addBlob(createBlob("hello")));
        assertEquals(35, storage.addBlob(createBlob("world")));
        assertEquals(44, storage.addBlob(createBlob("foo")));
        assertEquals(51, storage.addBlob(createBlob("bar")));
        assertEquals(58, storage.getSize());
    }

    @Test
    public void replaceBlob() throws IOException {
        DynamicBlobStorage storage = createStorage();
        assertEquals(26, storage.addBlob(createBlob("foo")));
        assertEquals(33, storage.addBlob(createBlob("hello")));
        assertEquals(42, storage.getSize());

        assertEquals(42, storage.putBlob(26, createBlob("world")));
        assertEquals(33, storage.putBlob(33, createBlob("bar")));
        assertEquals(51, storage.getSize());

        assertEquals("world", parseBlob(storage.getBlob(42)));
        assertEquals("bar", parseBlob(storage.getBlob(33)));
    }

    @Test
    public void addAndReplaceRandomBlobs() throws IOException {
        Random random = new Random(0);
        final int iter = 1000, positions = 20;
        String[] strings = new String[iter];

        // Generate random strings of random length
        for (int i = 0; i < iter; i++) {
            int length = random.nextInt(20)+3;
            StringBuilder sb = new StringBuilder(length);
            for (int j = 0; j < length; j++) {
                sb.append((char)(random.nextInt(26) + 'a'));
            }
            strings[i] = sb.toString();
        }

        DynamicBlobStorage storage = createStorage();
        int[] ofs = new int[positions], expected = new int[positions];
        for (int i = 0; i < positions; i++) {
            ofs[i] = -1;
        }
        for (int i = 0; i < iter; i++) {
            ByteBuffer blob = createBlob(strings[i]);
            if (i < positions) {
                ofs[i] = storage.addBlob(blob);
                expected[i] = i;
            } else {
                ofs[i % positions] = storage.putBlob(ofs[i % positions], blob);
                expected[i % positions] = i;
            }

            for (int j = 0; j < positions; j++) {
                if (ofs[j] >= 0) {
                    String actual = parseBlob(storage.getBlob(ofs[j]));
                    assertEquals(strings[expected[j]], actual);
                }
            }
        }
    }

    @Test
    public void testInsert() throws IOException {
        DynamicBlobStorage storage = createStorage();
        int ofs1 = storage.addBlob(createBlob("foo"));
        int ofs2 = storage.addBlob(createBlob("bar"));
        int ofs3 = storage.addBlob(createBlob("yo"));

        storage.insert(ofs2, 8);

        assertEquals("foo", parseBlob(storage.getBlob(ofs1)));
        assertEquals("bar", parseBlob(storage.getBlob(ofs2 + 8)));
        assertEquals("yo", parseBlob(storage.getBlob(ofs3 + 8)));
    }

    @Test
    public void testRandomInserts() throws IOException {
        Random random = new Random(0);
        int n = 10, stringSize = 10000;

        String[] largeStrings = new String[n];
        for (int i = 0; i < n; i++) {
            StringBuilder sb = new StringBuilder(stringSize);
            for (int j = 0; j < stringSize; j++) {
                sb.append((char)('a' + random.nextInt(26)));
            }
            largeStrings[i] = sb.toString();
        }

        // Ensure strings are larger than the chunk the data is moved with
        assertTrue(largeStrings[0].length() > CHUNK_SIZE);

        DynamicBlobStorage storage = createStorage();
        int[] ofs = new int[n];
        for (int i = 0; i < n; i++) {
            ofs[i] = storage.addBlob(createBlob(largeStrings[i]));
        }

        // Do 100 random insert operations, and update the affected offsets
        for (int i = 0; i < 100; i++) {
            int m = random.nextInt(n), bytes = random.nextInt(CHUNK_SIZE * 4);
            storage.insert(ofs[m], bytes);
            for (int j = m; j < n; j++) {
                ofs[j] += bytes;
            }
        }

        // Ensure all strings match still
        for (int i = 0; i < n; i++) {
            assertEquals("String " + i + " mismatches", largeStrings[i], parseBlob(storage.getBlob(ofs[i])));
        }
    }
}

package se.yarin.cbhlib.storage;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import se.yarin.cbhlib.storage.BlobSizeRetriever;
import se.yarin.cbhlib.storage.BlobStorage;
import se.yarin.cbhlib.storage.FileBlobStorage;
import se.yarin.cbhlib.util.CBUtil;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FileBlobStorageTest {
    private static final int CHUNK_SIZE = 4096;
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private BlobStorage createStorage() throws IOException {
        File file = folder.newFile();
        file.delete();
        FileBlobStorage.createEmptyStorage(file);
        return new FileBlobStorage(file, new StringBlobSizeRetriever(), CHUNK_SIZE, CHUNK_SIZE);
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
        BlobStorage storage = createStorage();
        int ofs = storage.writeBlob(createBlob("hello world"));
        assertEquals("hello world", parseBlob(storage.readBlob(ofs)));
    }

    @Test
    public void addMultipleBlobs() throws IOException {
        BlobStorage storage = createStorage();
        assertEquals(26, storage.writeBlob(createBlob("hello")));
        assertEquals(35, storage.writeBlob(createBlob("world")));
        assertEquals(44, storage.writeBlob(createBlob("foo")));
        assertEquals(51, storage.writeBlob(createBlob("bar")));
        assertEquals(58, storage.getSize());
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

        BlobStorage storage = createStorage();
        StringBlobSizeRetriever sizeRetriever = new StringBlobSizeRetriever();
        int[] ofs = new int[positions], expected = new int[positions];
        for (int i = 0; i < positions; i++) {
            ofs[i] = -1;
        }
        for (int i = 0; i < iter; i++) {
            ByteBuffer blob = createBlob(strings[i]);
            if (i < positions) {
                ofs[i] = storage.writeBlob(blob);
                expected[i] = i;
            } else {
                int oldSize = sizeRetriever.getBlobSize(storage.readBlob(ofs[i % positions]));
                int delta = sizeRetriever.getBlobSize(blob) - oldSize;
                if (delta > 0) {
                    storage.insert(ofs[i % positions], delta);
                    for (int j = i % positions + 1; j < positions; j++) {
                        if (ofs[j] >= 0) ofs[j] += delta;
                    }
                }
                storage.writeBlob(ofs[i % positions], blob);
                expected[i % positions] = i;
            }

            for (int j = 0; j < positions; j++) {
                if (ofs[j] >= 0) {
                    String actual = parseBlob(storage.readBlob(ofs[j]));
                    assertEquals(strings[expected[j]], actual);
                }
            }
        }
    }

    @Test
    public void testInsert() throws IOException {
        BlobStorage storage = createStorage();
        int ofs1 = storage.writeBlob(createBlob("foo"));
        int ofs2 = storage.writeBlob(createBlob("bar"));
        int ofs3 = storage.writeBlob(createBlob("yo"));

        storage.insert(ofs2, 8);

        assertEquals("foo", parseBlob(storage.readBlob(ofs1)));
        assertEquals("bar", parseBlob(storage.readBlob(ofs2 + 8)));
        assertEquals("yo", parseBlob(storage.readBlob(ofs3 + 8)));
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

        BlobStorage storage = createStorage();
        int[] ofs = new int[n];
        for (int i = 0; i < n; i++) {
            ofs[i] = storage.writeBlob(createBlob(largeStrings[i]));
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
            assertEquals("String " + i + " mismatches", largeStrings[i], parseBlob(storage.readBlob(ofs[i])));
        }
    }
}

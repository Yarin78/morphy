package se.yarin.morphy.storage;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import se.yarin.morphy.DatabaseContext;
import se.yarin.morphy.ResourceLoader;
import se.yarin.morphy.util.CBUtil;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Random;
import java.util.Set;

import static java.nio.file.StandardOpenOption.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FileBlobStorageTest {
    private static final int CHUNK_SIZE = 4096;
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private final DatabaseContext context = new DatabaseContext();

    private BlobStorage createStorage() throws IOException {
        File file = folder.newFile();
        file.delete();
        FileBlobStorage storage = new FileBlobStorage(file, context, new StringBlobSizeRetriever(), Set.of(READ, WRITE, CREATE_NEW));
        // The default chunk size is much bigger; this will ensure the insert operations actually moves stuff
        storage.getChannel().setChunkSize(CHUNK_SIZE);
        return storage;
    }

    static ByteBuffer createBlob(String value) {
        ByteBuffer blob = ByteBuffer.allocate(value.length() + 4);
        blob.putInt(value.length() + 4);
        blob.put(value.getBytes(CBUtil.cbCharSet));
        blob.flip();
        return blob;
    }

    static String parseBlob(ByteBuffer blob) {
        int length = blob.getInt();
        assertEquals(length - 4, blob.remaining());
        return new String(blob.array(), 4, length - 4, CBUtil.cbCharSet);
    }

    static class StringBlobSizeRetriever implements BlobSizeRetriever {
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
        long offset = storage.appendBlob(createBlob("hello world"));
        assertEquals("hello world", parseBlob(storage.getBlob(offset)));
    }

    @Test
    public void addMultipleBlobs() throws IOException {
        BlobStorage storage = createStorage();
        assertEquals(26, storage.appendBlob(createBlob("hello")));
        assertEquals(35, storage.appendBlob(createBlob("world")));
        assertEquals(44, storage.appendBlob(createBlob("foo")));
        assertEquals(51, storage.appendBlob(createBlob("bar")));
        assertEquals(58, storage.getSize());
        assertEquals(0, storage.getWastedBytes());
    }

    @Test
    public void replaceBlobWithShorter() throws IOException {
        BlobStorage storage = createStorage();
        long ofs = storage.appendBlob(createBlob("hello"));
        assertEquals(0, storage.getWastedBytes());
        storage.removeBlob(ofs);
        storage.putBlob(ofs, createBlob("foo"));
        assertEquals(2, storage.getWastedBytes());
    }

    @Test
    public void removeBlob() throws IOException {
        BlobStorage storage = createStorage();
        long ofs = storage.appendBlob(createBlob("hello"));
        long ofs2 = storage.appendBlob(createBlob("world"));
        assertEquals(0, storage.getWastedBytes());
        storage.removeBlob(ofs);
        assertEquals(9, storage.getWastedBytes());
        storage.removeBlob(ofs2);
        assertEquals(18, storage.getWastedBytes());
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
        long[] offset = new long[positions];
        int[] expected = new int[positions];
        for (int i = 0; i < positions; i++) {
            offset[i] = -1;
        }
        for (int i = 0; i < iter; i++) {
            ByteBuffer blob = createBlob(strings[i]);
            if (i < positions) {
                offset[i] = storage.appendBlob(blob);
                expected[i] = i;
            } else {
                int oldSize = sizeRetriever.getBlobSize(storage.getBlob(offset[i % positions]));
                storage.removeBlob(offset[i % positions]);
                int delta = sizeRetriever.getBlobSize(blob) - oldSize;
                if (delta > 0) {
                    if (i % positions + 1 < positions) {
                        storage.insert(offset[i % positions + 1], delta);
                    }
                    for (int j = i % positions + 1; j < positions; j++) {
                        if (offset[j] >= 0) offset[j] += delta;
                    }
                }
                storage.putBlob(offset[i % positions], blob);
                expected[i % positions] = i;
            }

            for (int j = 0; j < positions; j++) {
                if (offset[j] >= 0) {
                    String actual = parseBlob(storage.getBlob(offset[j]));
                    assertEquals(strings[expected[j]], actual);
                }
            }
        }

        int totSize = storage.getHeader().headerSize();
        for (int i = 0; i < positions; i++) {
            totSize += sizeRetriever.getBlobSize(storage.getBlob(offset[i]));
        }
        long expectedWaste = storage.getSize() - totSize;
        assertEquals(expectedWaste, storage.getWastedBytes());
    }

    @Test
    public void testGetBlobsFromStorageWithShortHeader() throws IOException {
        File shortheader = ResourceLoader.materializeStream("shortheader",
                FileBlobStorage.class.getResourceAsStream("shortheader.blobstorage.bin"),
                ".bin");
        FileBlobStorage storage = new FileBlobStorage(shortheader, context, new StringBlobSizeRetriever(), Set.of(READ));
        assertEquals(10, storage.getHeader().headerSize());
        assertEquals(29, storage.getHeader().size());

        assertEquals("first", parseBlob(storage.getBlob(10)));
        assertEquals("second", parseBlob(storage.getBlob(19)));
    }

    @Test
    public void testWriteBlobsToStorageWithShortHeader() throws IOException {
        File file = ResourceLoader.materializeStream("shortheader",
                FileBlobStorage.class.getResourceAsStream("shortheader.blobstorage.bin"),
                ".bin");
        FileBlobStorage storage = new FileBlobStorage(file, context, new StringBlobSizeRetriever(), Set.of(READ, WRITE));
        assertEquals(10, storage.getHeader().headerSize());

        long offset = storage.appendBlob(createBlob("third"));
        storage.putBlob(19, createBlob("done"));

        assertEquals("first", parseBlob(storage.getBlob(10)));
        assertEquals("third", parseBlob(storage.getBlob(offset)));
        assertEquals("done", parseBlob(storage.getBlob(19)));

        assertEquals(10, storage.getHeader().headerSize());
        assertEquals(38, storage.getSize());
        assertEquals(38, storage.getHeader().size());
    }

    @Test
    public void testInsert() throws IOException {
        BlobStorage storage = createStorage();
        long ofs1 = storage.appendBlob(createBlob("foo"));
        long ofs2 = storage.appendBlob(createBlob("bar"));
        long ofs3 = storage.appendBlob(createBlob("yo"));

        int oldSize = storage.getHeader().size();
        storage.insert(ofs2, 8);
        assertEquals(oldSize + 8, storage.getHeader().size());

        assertEquals("foo", parseBlob(storage.getBlob(ofs1)));
        assertEquals("bar", parseBlob(storage.getBlob(ofs2 + 8)));
        assertEquals("yo", parseBlob(storage.getBlob(ofs3 + 8)));

        assertEquals(8, storage.getWastedBytes());
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
        long[] ofs = new long[n];
        for (int i = 0; i < n; i++) {
            ofs[i] = storage.appendBlob(createBlob(largeStrings[i]));
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

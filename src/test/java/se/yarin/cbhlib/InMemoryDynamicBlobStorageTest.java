package se.yarin.cbhlib;

import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.Random;

import static org.junit.Assert.assertEquals;

public class InMemoryDynamicBlobStorageTest {
    private ByteBuffer createBlob(String value) {
        ByteBuffer blob = ByteBuffer.allocate(value.length() + 2);
        blob.putShort((short) (value.length() + 2));
        blob.put(value.getBytes(CBUtil.cbCharSet));
        blob.flip();
        return blob;
    }

    private String parseBlob(ByteBuffer blob) {
        short length = blob.getShort();
        Assert.assertEquals(length - 2, blob.remaining());
        return new String(blob.array(), 2, length - 2, CBUtil.cbCharSet);
    }

    private class StringBlobSizeRetriever implements BlobSizeRetriever {
        @Override
        public int getBlobSize(ByteBuffer buf) {
            return buf.getShort(buf.position());
        }
    }

    @Test
    public void addAndRetrieveBlobToStorage() {
        InMemoryDynamicBlobStorage storage = new InMemoryDynamicBlobStorage(new StringBlobSizeRetriever());
        int ofs = storage.addBlob(createBlob("hello world"));
        Assert.assertEquals("hello world", parseBlob(storage.getBlob(ofs)));
    }

    @Test
    public void addMultipleBlobs() {
        InMemoryDynamicBlobStorage storage = new InMemoryDynamicBlobStorage(new StringBlobSizeRetriever());
        Assert.assertEquals(1, storage.addBlob(createBlob("hello")));
        Assert.assertEquals(8, storage.addBlob(createBlob("world")));
        Assert.assertEquals(15, storage.addBlob(createBlob("foo")));
        Assert.assertEquals(20, storage.addBlob(createBlob("bar")));
        Assert.assertEquals(25, storage.getSize());
    }

    @Test
    public void replaceBlob() {
        InMemoryDynamicBlobStorage storage = new InMemoryDynamicBlobStorage(new StringBlobSizeRetriever());
        Assert.assertEquals(1, storage.addBlob(createBlob("foo")));
        Assert.assertEquals(6, storage.addBlob(createBlob("hello")));
        Assert.assertEquals(13, storage.getSize());

        Assert.assertEquals(13, storage.putBlob(0, createBlob("world")));
        Assert.assertEquals(6, storage.putBlob(6, createBlob("bar")));
        Assert.assertEquals(20, storage.getSize());

        Assert.assertEquals("world", parseBlob(storage.getBlob(13)));
        Assert.assertEquals("bar", parseBlob(storage.getBlob(6)));
    }

    @Test
    public void addAndReplaceRandomBlobs() {
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

        InMemoryDynamicBlobStorage storage = new InMemoryDynamicBlobStorage(new StringBlobSizeRetriever());
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
                    Assert.assertEquals(strings[expected[j]], actual);
                }
            }
        }
    }

    @Test
    public void testInsert() {
        InMemoryDynamicBlobStorage storage = new InMemoryDynamicBlobStorage(new StringBlobSizeRetriever());
        int ofs1 = storage.addBlob(createBlob("foo"));
        int ofs2 = storage.addBlob(createBlob("bar"));
        int ofs3 = storage.addBlob(createBlob("yo"));

        storage.insert(ofs2, 8);

        assertEquals("foo", parseBlob(storage.getBlob(ofs1)));
        assertEquals("bar", parseBlob(storage.getBlob(ofs2 + 8)));
        assertEquals("yo", parseBlob(storage.getBlob(ofs3 + 8)));
    }

}

package se.yarin.cbhlib;

import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.Random;

import static org.junit.Assert.assertEquals;

public class InMemoryBlobStorageTest {
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
        InMemoryBlobStorage storage = new InMemoryBlobStorage(new StringBlobSizeRetriever());
        int ofs = storage.writeBlob(createBlob("hello world"));
        Assert.assertEquals("hello world", parseBlob(storage.readBlob(ofs)));
    }

    @Test
    public void addMultipleBlobs() {
        InMemoryBlobStorage storage = new InMemoryBlobStorage(new StringBlobSizeRetriever());
        Assert.assertEquals(1, storage.writeBlob(createBlob("hello")));
        Assert.assertEquals(8, storage.writeBlob(createBlob("world")));
        Assert.assertEquals(15, storage.writeBlob(createBlob("foo")));
        Assert.assertEquals(20, storage.writeBlob(createBlob("bar")));
        Assert.assertEquals(25, storage.getSize());
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

        StringBlobSizeRetriever sizeRetriever = new StringBlobSizeRetriever();
        InMemoryBlobStorage storage = new InMemoryBlobStorage(sizeRetriever);
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
                    Assert.assertEquals(strings[expected[j]], actual);
                }
            }
        }
    }

    @Test
    public void testInsert() {
        InMemoryBlobStorage storage = new InMemoryBlobStorage(new StringBlobSizeRetriever());
        int ofs1 = storage.writeBlob(createBlob("foo"));
        int ofs2 = storage.writeBlob(createBlob("bar"));
        int ofs3 = storage.writeBlob(createBlob("yo"));

        storage.insert(ofs2, 8);

        assertEquals("foo", parseBlob(storage.readBlob(ofs1)));
        assertEquals("bar", parseBlob(storage.readBlob(ofs2 + 8)));
        assertEquals("yo", parseBlob(storage.readBlob(ofs3 + 8)));
    }

}

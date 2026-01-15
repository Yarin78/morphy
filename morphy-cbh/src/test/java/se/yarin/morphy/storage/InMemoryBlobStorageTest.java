package se.yarin.morphy.storage;

import org.junit.Assert;
import org.junit.Test;
import se.yarin.morphy.ResourceLoader;
import se.yarin.morphy.util.CBUtil;

import java.io.IOException;
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

  static class StringBlobSizeRetriever implements BlobSizeRetriever {
    @Override
    public int getBlobSize(ByteBuffer buf) {
      return buf.getShort(buf.position());
    }
  }

  @Test
  public void addAndRetrieveBlobToStorage() {
    InMemoryBlobStorage storage = new InMemoryBlobStorage(new StringBlobSizeRetriever());
    long ofs = storage.appendBlob(createBlob("hello world"));
    Assert.assertEquals("hello world", parseBlob(storage.getBlob(ofs)));
  }

  @Test
  public void addMultipleBlobs() {
    InMemoryBlobStorage storage = new InMemoryBlobStorage(new StringBlobSizeRetriever());
    Assert.assertEquals(26, storage.appendBlob(createBlob("hello")));
    Assert.assertEquals(33, storage.appendBlob(createBlob("world")));
    Assert.assertEquals(40, storage.appendBlob(createBlob("foo")));
    Assert.assertEquals(45, storage.appendBlob(createBlob("bar")));
    Assert.assertEquals(50, storage.getSize());
  }

  @Test
  public void replaceLastBlobWithShorter() {
    BlobStorage storage = new InMemoryBlobStorage(new StringBlobSizeRetriever());
    long ofs = storage.appendBlob(createBlob("hello"));
    assertEquals(0, storage.getWastedBytes());
    storage.removeBlob(ofs);
    storage.putBlob(ofs, createBlob("foo"));
    assertEquals(2, storage.getWastedBytes());
  }

  @Test
  public void replaceLastBlobWithLonger() {
    BlobStorage storage = new InMemoryBlobStorage(new StringBlobSizeRetriever());
    long ofs = storage.appendBlob(createBlob("hello"));
    assertEquals(0, storage.getWastedBytes());
    storage.removeBlob(ofs);
    storage.putBlob(ofs, createBlob("hello world"));
    assertEquals(0, storage.getWastedBytes());
  }

  @Test
  public void replaceFirstBlobWithShorter() {
    BlobStorage storage = new InMemoryBlobStorage(new StringBlobSizeRetriever());
    long ofs = storage.appendBlob(createBlob("hello"));
    storage.appendBlob(createBlob("world"));
    assertEquals(0, storage.getWastedBytes());
    storage.removeBlob(ofs);
    storage.putBlob(ofs, createBlob("foo"));
    assertEquals(2, storage.getWastedBytes());
  }

  @Test
  public void replaceFirstBlobWithLonger() {
    BlobStorage storage = new InMemoryBlobStorage(new StringBlobSizeRetriever());
    long ofs = storage.appendBlob(createBlob("hello"));
    long ofs2 = storage.appendBlob(createBlob("world"));
    assertEquals(0, storage.getWastedBytes());
    storage.insert(ofs2, 6);
    storage.removeBlob(ofs);
    storage.putBlob(ofs, createBlob("hello world"));
    assertEquals(0, storage.getWastedBytes());
  }

  @Test
  public void removeBlob() {
    BlobStorage storage = new InMemoryBlobStorage(new StringBlobSizeRetriever());
    long ofs = storage.appendBlob(createBlob("hello"));
    long ofs2 = storage.appendBlob(createBlob("world"));
    assertEquals(0, storage.getWastedBytes());
    storage.removeBlob(ofs);
    assertEquals(7, storage.getWastedBytes());
    storage.removeBlob(ofs2);
    assertEquals(14, storage.getWastedBytes());
  }

  @Test
  public void addAndReplaceRandomBlobs() {
    Random random = new Random(0);
    final int iter = 1000, positions = 20;
    String[] strings = new String[iter];

    // Generate random strings of random length
    for (int i = 0; i < iter; i++) {
      int length = random.nextInt(20) + 3;
      StringBuilder sb = new StringBuilder(length);
      for (int j = 0; j < length; j++) {
        sb.append((char) (random.nextInt(26) + 'a'));
      }
      strings[i] = sb.toString();
    }

    StringBlobSizeRetriever sizeRetriever = new StringBlobSizeRetriever();
    InMemoryBlobStorage storage = new InMemoryBlobStorage(sizeRetriever);
    long[] ofs = new long[positions];
    int[] expected = new int[positions];
    for (int i = 0; i < positions; i++) {
      ofs[i] = -1;
    }
    for (int i = 0; i < iter; i++) {
      ByteBuffer blob = createBlob(strings[i]);
      if (i < positions) {
        ofs[i] = storage.appendBlob(blob);
        expected[i] = i;
      } else {
        int oldSize = sizeRetriever.getBlobSize(storage.getBlob(ofs[i % positions]));
        storage.removeBlob(ofs[i % positions]);
        int delta = sizeRetriever.getBlobSize(blob) - oldSize;
        if (delta > 0) {
          if (i % positions + 1 < positions) {
            storage.insert(ofs[i % positions + 1], delta);
          }
          for (int j = i % positions + 1; j < positions; j++) {
            if (ofs[j] >= 0) ofs[j] += delta;
          }
        }
        storage.putBlob(ofs[i % positions], blob);
        expected[i % positions] = i;
      }

      for (int j = 0; j < positions; j++) {
        if (ofs[j] >= 0) {
          String actual = parseBlob(storage.getBlob(ofs[j]));
          Assert.assertEquals(strings[expected[j]], actual);
        }
      }
    }

    int totSize = storage.getHeader().headerSize();
    for (int i = 0; i < positions; i++) {
      totSize += sizeRetriever.getBlobSize(storage.getBlob(ofs[i]));
    }
    long expectedWaste = storage.getSize() - totSize;
    assertEquals(expectedWaste, storage.getWastedBytes());
  }

  @Test
  public void testGetBlobsFromStorageWithShortHeader() throws IOException {
    ByteBuffer buf =
        ResourceLoader.loadResource(FileBlobStorage.class, "shortheader.blobstorage.bin");
    InMemoryBlobStorage storage =
        new InMemoryBlobStorage(buf, new FileBlobStorageTest.StringBlobSizeRetriever());
    assertEquals(10, storage.getHeader().headerSize());
    assertEquals(29, storage.getHeader().size());

    assertEquals("first", FileBlobStorageTest.parseBlob(storage.getBlob(10)));
    assertEquals("second", FileBlobStorageTest.parseBlob(storage.getBlob(19)));
  }

  @Test
  public void testWriteBlobsToStorageWithShortHeader() throws IOException {
    ByteBuffer buf =
        ResourceLoader.loadResource(FileBlobStorage.class, "shortheader.blobstorage.bin");
    InMemoryBlobStorage storage =
        new InMemoryBlobStorage(buf, new FileBlobStorageTest.StringBlobSizeRetriever());
    assertEquals(10, storage.getHeader().headerSize());

    long offset = storage.appendBlob(FileBlobStorageTest.createBlob("third"));
    storage.putBlob(19, FileBlobStorageTest.createBlob("done"));

    assertEquals("first", FileBlobStorageTest.parseBlob(storage.getBlob(10)));
    assertEquals("third", FileBlobStorageTest.parseBlob(storage.getBlob(offset)));
    assertEquals("done", FileBlobStorageTest.parseBlob(storage.getBlob(19)));

    assertEquals(10, storage.getHeader().headerSize());
    assertEquals(38, storage.getSize());
    assertEquals(38, storage.getHeader().size());
  }

  @Test
  public void testInsert() {
    InMemoryBlobStorage storage = new InMemoryBlobStorage(new StringBlobSizeRetriever());
    long ofs1 = storage.appendBlob(createBlob("foo"));
    long ofs2 = storage.appendBlob(createBlob("bar"));
    long ofs3 = storage.appendBlob(createBlob("yo"));

    int oldSize = storage.getHeader().size();
    storage.insert(ofs2, 8);
    assertEquals(oldSize + 8, storage.getHeader().size());

    assertEquals("foo", parseBlob(storage.getBlob(ofs1)));
    assertEquals("bar", parseBlob(storage.getBlob(ofs2 + 8)));
    assertEquals("yo", parseBlob(storage.getBlob(ofs3 + 8)));
  }
}

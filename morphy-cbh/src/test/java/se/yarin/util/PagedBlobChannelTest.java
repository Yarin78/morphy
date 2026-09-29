package se.yarin.util;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.Assert.*;

public class PagedBlobChannelTest {

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  /**
   * The byte at a position of the test file: a hash of the position, so that a page read from the
   * wrong place doesn't look like the right one, as it would with just the position's low byte.
   */
  private static byte expected(long position) {
    return (byte) ((position * 2654435761L) >>> 13);
  }

  private File patternFile(int size) throws Exception {
    byte[] data = new byte[size];
    for (int i = 0; i < size; i++) {
      data[i] = expected(i);
    }
    File file = folder.newFile("pattern.bin");
    Files.write(file.toPath(), data);
    return file;
  }

  private static void assertPattern(ByteBuffer buf, long offset) {
    for (int i = 0; i < buf.limit(); i++) {
      assertEquals("byte at " + (offset + i), expected(offset + i), buf.get(i));
    }
  }

  @Test
  public void readsAcrossPages() throws Exception {
    File file = patternFile(PagedBlobChannel.PAGE_SIZE * 3 + 100);
    PagedBlobChannel channel = PagedBlobChannel.open(file.toPath(), StandardOpenOption.READ);
    try {
      long offset = PagedBlobChannel.PAGE_SIZE - 10;
      assertPattern(channel.read(offset, PagedBlobChannel.PAGE_SIZE + 20), offset);
    } finally {
      channel.close();
    }
  }

  @Test
  public void concurrentReadsSeeTheFile() throws Exception {
    // More pages than the cache holds, so pages keep being read from the file while other threads
    // read too; reading a page used to move the channel's shared position
    int size = PagedBlobChannel.PAGE_SIZE * 40;
    File file = patternFile(size);
    PagedBlobChannel channel = PagedBlobChannel.open(file.toPath(), StandardOpenOption.READ);
    ExecutorService pool = Executors.newFixedThreadPool(16);
    try {
      List<Future<?>> results = new ArrayList<>();
      for (int t = 0; t < 16; t++) {
        long seed = t;
        results.add(
            pool.submit(
                () -> {
                  Random random = new Random(seed);
                  for (int i = 0; i < 20000; i++) {
                    int length = 1 + random.nextInt(100);
                    long offset = random.nextInt(size - length);
                    assertPattern(channel.read(offset, length), offset);
                  }
                  return null;
                }));
      }
      for (Future<?> result : results) {
        result.get();
      }
    } finally {
      pool.shutdown();
      channel.close();
    }
  }
}

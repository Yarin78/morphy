package se.yarin.morphy.pgn;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class PgnIndexTest {
  @Rule public TemporaryFolder tmp = new TemporaryFolder();

  private static final String GAME_1 = "[Event \"A\"]\n[Result \"1-0\"]\n\n1. e4 e5 1-0\n\n";
  private static final String GAME_2 = "[Event \"B\"]\n[Result \"0-1\"]\n\n1. d4 d5 0-1\n\n";

  private Path pgn(String contents) throws IOException {
    Path path = tmp.getRoot().toPath().resolve("games.pgn");
    Files.writeString(path, contents);
    return path;
  }

  @Test
  public void indexNextToThePgnFileHasTheSameNameWithPgiExtension() {
    assertEquals("World ch.pgi", PgnIndex.indexPath(Path.of("/x/World ch.pgn")).getFileName().toString());
    assertEquals("a.pgi", PgnIndex.indexPath(Path.of("a.PGN")).getFileName().toString());
  }

  @Test
  public void fileLayoutIsLittleEndianCountThenOffsets() throws IOException {
    Path pgn = pgn(GAME_1 + GAME_2);
    long[] offsets = PgnIndex.load(pgn, true);
    long second = GAME_1.length();
    assertArrayEquals(new long[] {0, second, second + GAME_2.length()}, offsets);

    byte[] bytes = Files.readAllBytes(PgnIndex.indexPath(pgn));
    assertEquals(4 * 8, bytes.length);
    ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
    assertEquals(3, buf.getLong()); // the number of offsets, one more than the number of games
    assertEquals(0, buf.getLong());
    assertEquals(second, buf.getLong());
    assertEquals(second + GAME_2.length(), buf.getLong());
  }

  @Test
  public void aMatchingIndexIsUsedWithoutScanning() throws IOException {
    String contents = GAME_1 + GAME_2 + GAME_1;
    Path pgn = pgn(contents);
    // A valid index that leaves out the second game; a scan would find all three
    long third = GAME_1.length() + GAME_2.length();
    long[] partial = {0, third, contents.length()};
    PgnIndex.write(PgnIndex.indexPath(pgn), partial);

    assertArrayEquals(partial, PgnIndex.load(pgn, false));
  }

  @Test
  public void anIndexOfAnotherSizeIsRebuilt() throws IOException {
    Path pgn = pgn(GAME_1);
    PgnIndex.load(pgn, true);
    Files.writeString(pgn, GAME_1 + GAME_2);

    long[] offsets = PgnIndex.load(pgn, true);
    assertEquals(3, offsets.length);
    assertArrayEquals(offsets, PgnIndex.read(PgnIndex.indexPath(pgn)));
  }

  @Test
  public void anIndexPointingAtTheWrongPlaceIsRebuilt() throws IOException {
    String contents = GAME_1 + GAME_2;
    Path pgn = pgn(contents);
    // The right size, but the second game doesn't start where it says
    PgnIndex.write(PgnIndex.indexPath(pgn), new long[] {0, 5, contents.length()});

    long[] offsets = PgnIndex.load(pgn, true);
    assertArrayEquals(new long[] {0, GAME_1.length(), contents.length()}, offsets);
  }

  @Test
  public void garbageIndexIsRebuilt() throws IOException {
    Path pgn = pgn(GAME_1 + GAME_2);
    Files.write(PgnIndex.indexPath(pgn), "not an index".getBytes(StandardCharsets.UTF_8));
    assertEquals(3, PgnIndex.load(pgn, true).length);

    Files.write(PgnIndex.indexPath(pgn), new byte[0]);
    assertEquals(3, PgnIndex.load(pgn, true).length);
  }

  @Test
  public void readOnlyNeverWritesTheIndex() throws IOException {
    Path pgn = pgn(GAME_1 + GAME_2);
    assertEquals(3, PgnIndex.load(pgn, false).length);
    assertFalse(Files.exists(PgnIndex.indexPath(pgn)));
  }

  @Test
  public void emptyFileIsAnIndexOfNoGames() throws IOException {
    Path pgn = pgn("");
    assertArrayEquals(new long[] {0}, PgnIndex.load(pgn, true));
    assertTrue(Files.exists(PgnIndex.indexPath(pgn)));
    assertArrayEquals(new long[] {0}, PgnIndex.load(pgn, false));
  }
}

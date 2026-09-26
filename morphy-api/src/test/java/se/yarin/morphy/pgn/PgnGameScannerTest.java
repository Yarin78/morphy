package se.yarin.morphy.pgn;

import static org.junit.Assert.assertArrayEquals;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

public class PgnGameScannerTest {

  private static long[] scan(String pgn) throws IOException {
    return scan(pgn.getBytes(StandardCharsets.UTF_8));
  }

  private static long[] scan(byte[] pgn) throws IOException {
    return PgnGameScanner.scan(new ByteArrayInputStream(pgn));
  }

  private static final String GAME_1 = "[Event \"A\"]\n[Result \"1-0\"]\n\n1. e4 e5 1-0\n\n";
  private static final String GAME_2 = "[Event \"B\"]\n[Result \"0-1\"]\n\n1. d4 d5 0-1\n\n";

  @Test
  public void emptyFileHasNoGames() throws IOException {
    assertArrayEquals(new long[] {0}, scan(""));
  }

  @Test
  public void offsetsAreGameStartsFollowedBySize() throws IOException {
    int second = GAME_1.length();
    assertArrayEquals(
        new long[] {0, second, second + GAME_2.length()}, scan(GAME_1 + GAME_2));
  }

  @Test
  public void byteOrderMarkBelongsToNoGame() throws IOException {
    byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    byte[] game = GAME_1.getBytes(StandardCharsets.UTF_8);
    byte[] all = new byte[bom.length + game.length];
    System.arraycopy(bom, 0, all, 0, bom.length);
    System.arraycopy(game, 0, all, bom.length, game.length);
    assertArrayEquals(new long[] {3, all.length}, scan(all));
    assertArrayEquals(new long[] {3}, scan(bom));
  }

  @Test
  public void crlfLineEndings() throws IOException {
    String pgn = (GAME_1 + GAME_2).replace("\n", "\r\n");
    int second = GAME_1.replace("\n", "\r\n").length();
    assertArrayEquals(new long[] {0, second, pgn.length()}, scan(pgn));
  }

  @Test
  public void multiByteCharactersAreCountedAsBytes() throws IOException {
    String first = "[Event \"Björn\"]\n[Result \"*\"]\n\n1. e4 *\n\n";
    long second = first.getBytes(StandardCharsets.UTF_8).length;
    assertArrayEquals(new long[] {0, second, second + GAME_2.length()}, scan(first + GAME_2));
  }

  @Test
  public void aLineStartingWithBracketInsideACommentDoesNotStartAGame() throws IOException {
    String game = "[Event \"A\"]\n[Result \"*\"]\n\n1. e4 { see\n[Event \"not a game\"]\nmore } e5 *\n\n";
    assertArrayEquals(new long[] {0, game.length()}, scan(game));
  }

  @Test
  public void aSemicolonCommentDoesNotOpenABraceComment() throws IOException {
    String first = "[Event \"A\"]\n[Result \"*\"]\n\n1. e4 ; a brace { in a line comment\ne5 *\n\n";
    assertArrayEquals(
        new long[] {0, first.length(), first.length() + GAME_2.length()}, scan(first + GAME_2));
  }

  @Test
  public void escapeLinesAreSkipped() throws IOException {
    String game = "%[Event \"escaped\"]\n[Event \"A\"]\n[Result \"*\"]\n\n1. e4 *\n\n";
    assertArrayEquals(new long[] {game.indexOf("[Event \"A\""), game.length()}, scan(game));
  }

  @Test
  public void tagsWithBracesInTheirValuesAreNotComments() throws IOException {
    String first = "[Event \"{unbalanced\"]\n[Result \"*\"]\n\n1. e4 *\n\n";
    assertArrayEquals(
        new long[] {0, first.length(), first.length() + GAME_2.length()}, scan(first + GAME_2));
  }

  @Test
  public void aGameCanStartWithoutABlankLineBeforeIt() throws IOException {
    String first = "[Event \"A\"]\n[Result \"1-0\"]\n\n1. e4 e5 1-0\n";
    assertArrayEquals(
        new long[] {0, first.length(), first.length() + GAME_2.length()}, scan(first + GAME_2));
  }

  @Test
  public void lastGameWithoutTrailingNewline() throws IOException {
    String last = "[Event \"B\"]\n[Result \"*\"]\n\n1. d4 *";
    assertArrayEquals(
        new long[] {0, GAME_1.length(), GAME_1.length() + last.length()}, scan(GAME_1 + last));
  }

  @Test
  public void gamesLargerThanTheReadBuffer() throws IOException {
    String moves = "1. e4 e5 ".repeat(20000);
    String big = "[Event \"Big\"]\n[Result \"*\"]\n\n" + moves + "*\n\n";
    assertArrayEquals(
        new long[] {0, big.length(), big.length() + GAME_2.length()}, scan(big + GAME_2));
  }
}

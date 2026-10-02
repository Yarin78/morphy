package se.yarin.morphy.chessbase.annotations;

import java.util.EnumSet;
import org.junit.jupiter.api.Test;
import se.yarin.morphy.chessbase.GameHeaderFlags;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The magnitude boundaries were determined from Mega Database 2021: for every game that has the
 * flag, the magnitude bit is set exactly when the count reaches the boundary.
 */
class AnnotationStatisticsTest {

  @Test
  public void testCommentariesMagnitude() {
    AnnotationStatistics stats = new AnnotationStatistics();
    assertEquals(0, stats.getCommentariesMagnitude());
    stats.commentariesLength = 1;
    assertEquals(1, stats.getCommentariesMagnitude());
    stats.commentariesLength = 200;
    assertEquals(1, stats.getCommentariesMagnitude());
    stats.commentariesLength = 201;
    assertEquals(2, stats.getCommentariesMagnitude());
  }

  @Test
  public void testSymbolsMagnitude() {
    AnnotationStatistics stats = new AnnotationStatistics();
    stats.noSymbols = 9;
    assertEquals(1, stats.getSymbolsMagnitude());
    stats.noSymbols = 10;
    assertEquals(2, stats.getSymbolsMagnitude());
  }

  @Test
  public void testGraphicalSquaresMagnitude() {
    AnnotationStatistics stats = new AnnotationStatistics();
    stats.noGraphicalSquares = 9;
    assertEquals(1, stats.getGraphicalSquaresMagnitude());
    stats.noGraphicalSquares = 10;
    assertEquals(2, stats.getGraphicalSquaresMagnitude());
  }

  @Test
  public void testGraphicalArrowsMagnitude() {
    AnnotationStatistics stats = new AnnotationStatistics();
    stats.noGraphicalArrows = 5;
    assertEquals(1, stats.getGraphicalArrowsMagnitude());
    stats.noGraphicalArrows = 6;
    assertEquals(2, stats.getGraphicalArrowsMagnitude());
  }

  @Test
  public void testTimeSpentMagnitude() {
    AnnotationStatistics stats = new AnnotationStatistics();
    assertEquals(0, stats.getTimeSpentMagnitude());
    stats.noTimeSpent = 9;
    assertEquals(1, stats.getTimeSpentMagnitude());
    stats.noTimeSpent = 10;
    assertEquals(2, stats.getTimeSpentMagnitude());
  }

  @Test
  public void testTrainingMagnitude() {
    AnnotationStatistics stats = new AnnotationStatistics();
    assertEquals(0, stats.getTrainingMagnitude());
    stats.noTraining = 5;
    assertEquals(1, stats.getTrainingMagnitude());
    stats.noTraining = 6;
    assertEquals(2, stats.getTrainingMagnitude());
  }

  @Test
  public void testSoundPictureAndVideoAnnotationsKeptAsUnknownSetTheirFlags() {
    AnnotationStatistics stats = new AnnotationStatistics();
    ImmutableUnknownAnnotation.of(0x10, new byte[] {1}).updateStatistics(stats);
    ImmutableUnknownAnnotation.of(0x11, new byte[] {2}).updateStatistics(stats);
    assertEquals(EnumSet.of(GameHeaderFlags.EMBEDDED_AUDIO, GameHeaderFlags.EMBEDDED_PICTURE), stats.getFlags());

    // In Mega Database 2021, no game with a video annotation has the embedded video flag; only
    // guiding texts have it
    AnnotationStatistics video = new AnnotationStatistics();
    ImmutableUnknownAnnotation.of(0x20, new byte[] {1, 0x35, 'a', 'b'}).updateStatistics(video);
    assertEquals(EnumSet.noneOf(GameHeaderFlags.class), video.getFlags());
  }
}

package se.yarin.morphy.games.annotations;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class VideoAnnotationTest {

  @Test
  public void testVideoAnnotationDoesNotSetAnyFlag() {
    // In Mega Database 2021, 22 video annotations are on 17 games, and none of the games have the
    // embedded video flag. Only guiding texts do.
    AnnotationStatistics stats = new AnnotationStatistics();
    ImmutableVideoAnnotation.of(new byte[] {1, 0x35, 'a', 'b'}).updateStatistics(stats);
    assertTrue(stats.getFlags().isEmpty());
  }
}

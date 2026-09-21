package se.yarin.morphy.games.annotations;

import org.junit.Test;

import java.nio.ByteBuffer;

import static org.junit.Assert.assertEquals;

public class TimeControlAnnotationTest {

  private TimeControlAnnotation deserialize(int... bytes) {
    ByteBuffer buf = ByteBuffer.allocate(bytes.length);
    for (int b : bytes) {
      buf.put((byte) b);
    }
    buf.flip();
    return (TimeControlAnnotation)
        new TimeControlAnnotation.Serializer().deserialize(buf, bytes.length);
  }

  @Test
  public void testTwoStagesAndUnusedSerie() {
    // 90 minutes for 40 moves, then 30 minutes with a 30 second increment for the rest of the game
    TimeControlAnnotation tc =
        deserialize(
            0x00, 0x08, 0x3d, 0x60, 0x00, 0x00, 0x00, 0x00, 0x00, 0x28, 0x01,
            0x00, 0x02, 0xbf, 0x20, 0x00, 0x00, 0x0b, 0xb8, 0x03, 0xe8, 0x03,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00);
    assertEquals(2, tc.timeSeries().size());
    assertEquals(540000, tc.timeSeries().get(0).start());
    assertEquals(40, tc.timeSeries().get(0).moves());
    assertEquals(1, tc.timeSeries().get(0).type());
    assertEquals(180000, tc.timeSeries().get(1).start());
    assertEquals(3000, tc.timeSeries().get(1).increment());
    assertEquals(1000, tc.timeSeries().get(1).moves());
    assertEquals(3, tc.timeSeries().get(1).type());
  }

  @Test
  public void testUnusedSeriesAreSkipped() {
    // 25 minutes for the whole game and then two series that are not used
    TimeControlAnnotation tc =
        deserialize(
            0x00, 0x02, 0x49, 0xf0, 0x00, 0x00, 0x00, 0x00, 0x03, 0xe8, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00);
    assertEquals(1, tc.timeSeries().size());
    assertEquals(150000, tc.timeSeries().get(0).start());
    assertEquals(0, tc.timeSeries().get(0).type());
  }
}

package se.yarin.morphy.games;

import org.junit.Test;
import se.yarin.morphy.entities.Nation;
import se.yarin.morphy.entities.TournamentTimeControl;

import java.nio.ByteBuffer;

import static org.junit.Assert.assertEquals;

public class RatingTypeTest {

  private byte[] serialize(RatingType ratingType) {
    ByteBuffer buf = ByteBuffer.allocate(16);
    ratingType.serialize(buf);
    assertEquals(16, buf.position());
    return buf.array();
  }

  @Test
  public void testInternationalLayout() {
    byte[] data = serialize(RatingType.international(TournamentTimeControl.NORMAL));
    assertEquals(0, data[0]);
    assertEquals(1, data[1]); // International
    assertEquals(0, data[2]);
    assertEquals(1, data[3]); // Time control plus one
    assertEquals(0, data[4]);
    assertEquals('F', data[5]);
    assertEquals('I', data[6]);
    assertEquals('D', data[7]);
    assertEquals('E', data[8]);
  }

  @Test
  public void testNationalLayout() {
    byte[] data = serialize(RatingType.national(TournamentTimeControl.NORMAL, Nation.SWEDEN));
    assertEquals(0, data[0]);
    assertEquals(2, data[1]); // National
    assertEquals(0, data[2]); // The time control of the national rating
    assertEquals(100, data[3]); // ChessBase always stores 100 here for a national rating
    assertEquals(0, data[5]); // No name
  }

  @Test
  public void testRoundTrip() {
    for (RatingType ratingType :
        new RatingType[] {
          RatingType.international(TournamentTimeControl.NORMAL),
          RatingType.international(TournamentTimeControl.BLITZ),
          RatingType.international(TournamentTimeControl.CORRESPONDENCE),
          RatingType.national(TournamentTimeControl.NORMAL, Nation.SWEDEN),
          RatingType.national(TournamentTimeControl.RAPID, Nation.GERMANY),
        }) {
      ByteBuffer buf = ByteBuffer.wrap(serialize(ratingType));
      RatingType after = RatingType.deserialize(buf);
      assertEquals(ratingType.international(), after.international());
      assertEquals(ratingType.national(), after.national());
      assertEquals(ratingType.nation(), after.nation());
    }
  }
}

package se.yarin.morphy.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.List;
import org.junit.Test;

public class TimeControlDtoTest {

  private static final TimeControlDto CLASSICAL =
      new TimeControlDto(
          List.of(new TimeControlDto.Period(5400, 30, 40), new TimeControlDto.Period(1800, 30, null)));

  @Test
  public void writesThePgnTag() {
    assertEquals("40/5400+30:1800+30", CLASSICAL.toPgn());
    assertEquals(
        "300", new TimeControlDto(List.of(new TimeControlDto.Period(300, 0, null))).toPgn());
  }

  @Test
  public void readsThePgnTag() {
    assertEquals(CLASSICAL, TimeControlDto.fromPgn("40/5400+30:1800+30"));
    assertEquals(
        new TimeControlDto(List.of(new TimeControlDto.Period(180, 2, null))),
        TimeControlDto.fromPgn(" 180+2 "));
  }

  @Test
  public void unknownOrUnreadableTagsAreNoTimeControl() {
    assertNull(TimeControlDto.fromPgn(null));
    assertNull(TimeControlDto.fromPgn("?"));
    assertNull(TimeControlDto.fromPgn("-"));
    assertNull(TimeControlDto.fromPgn("*180"));
  }
}

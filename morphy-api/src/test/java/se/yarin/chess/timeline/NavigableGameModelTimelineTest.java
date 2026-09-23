package se.yarin.chess.timeline;

import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import se.yarin.chess.NavigableGameModel;

import static org.junit.Assert.assertEquals;

public class NavigableGameModelTimelineTest {

  private StringBuilder eventString;
  private NavigableGameModelTimeline timeline;

  private class StringAppenderEvent extends GameEvent {
    private char c;

    public StringAppenderEvent(char c) {
      this.c = c;
    }

    @Override
    public void apply(@NotNull NavigableGameModel model) throws GameEventException {
      if (Character.toUpperCase(this.c) == 'X') throw new GameEventException(this, model, "failed");
      eventString.append(this.c);
    }

    @Override
    boolean isIncremental() {
      return Character.isLowerCase(this.c);
    }
  }

  @Before
  public void setupTimeline() {
    timeline = new NavigableGameModelTimeline();
    timeline.addEvent(0, new StringAppenderEvent('A'));

    timeline.addEvent(100, new StringAppenderEvent('B'));

    timeline.addEvent(200, new StringAppenderEvent('C'));
    timeline.addEvent(200, new StringAppenderEvent('d'));
    timeline.addEvent(220, new StringAppenderEvent('e'));
    timeline.addEvent(230, new StringAppenderEvent('f'));
    timeline.addEvent(230, new StringAppenderEvent('g'));

    timeline.addEvent(300, new StringAppenderEvent('H'));
    timeline.addEvent(350, new StringAppenderEvent('i'));
    timeline.addEvent(399, new StringAppenderEvent('j'));

    timeline.addEvent(400, new StringAppenderEvent('K'));
    timeline.addEvent(500, new StringAppenderEvent('l'));

    timeline.addEvent(500, new StringAppenderEvent('M'));
    timeline.addEvent(550, new StringAppenderEvent('n'));

    eventString = new StringBuilder();
  }

  private void verifyAppliedEvents(String expected) {
    assertEquals(expected, eventString.toString());
    eventString = new StringBuilder();
  }

  @Test
  public void applyPlayTo() {
    timeline.playTo(0);
    verifyAppliedEvents("A");
    timeline.playTo(0);
    verifyAppliedEvents("");
    timeline.playTo(370);
    verifyAppliedEvents("BCdefgHi");
  }

  @Test
  public void applyJumpToFutureTimestamp() {
    timeline.jumpTo(250);
    verifyAppliedEvents("Cdefg");
  }

  @Test
  public void applyJumpToAfterLastEvent() {
    timeline.jumpTo(800);
    verifyAppliedEvents("Mn");
  }

  @Test
  public void applyJumpToEarlierTimestamp() {
    timeline.jumpTo(230);
    verifyAppliedEvents("Cdefg");
    timeline.jumpTo(399);
    verifyAppliedEvents("Hij");
  }

  @Test
  public void findCorrectFullUpdate() {
    timeline = new NavigableGameModelTimeline();
    timeline.addEvent(1000, new StringAppenderEvent('a'));
    timeline.addEvent(1000, new StringAppenderEvent('B'));
    timeline.addEvent(1000, new StringAppenderEvent('c'));
    timeline.addEvent(1000, new StringAppenderEvent('D'));
    timeline.addEvent(1000, new StringAppenderEvent('e'));
    timeline.addEvent(1000, new StringAppenderEvent('f'));
    timeline.addEvent(1000, new StringAppenderEvent('g'));
    timeline.addEvent(1000, new StringAppenderEvent('h'));
    timeline.addEvent(1000, new StringAppenderEvent('I'));
    timeline.addEvent(1000, new StringAppenderEvent('j'));
    timeline.jumpTo(1000);
    verifyAppliedEvents("Ij");
  }

  @Test
  public void jumpToBeforeFirstFullUpdate() {
    timeline = new NavigableGameModelTimeline();
    timeline.addEvent(100, new StringAppenderEvent('a'));
    timeline.addEvent(200, new StringAppenderEvent('b'));
    timeline.addEvent(300, new StringAppenderEvent('C'));
    timeline.addEvent(400, new StringAppenderEvent('D'));
    timeline.addEvent(500, new StringAppenderEvent('e'));
    timeline.jumpTo(150);
    verifyAppliedEvents("a");
  }

  @Test
  public void testFailedEvents() {
    timeline = new NavigableGameModelTimeline();
    timeline.addEvent(0, new StringAppenderEvent('A'));
    timeline.addEvent(1000, new StringAppenderEvent('b'));
    timeline.addEvent(2000, new StringAppenderEvent('x'));
    timeline.addEvent(3000, new StringAppenderEvent('c'));
    timeline.addEvent(4000, new StringAppenderEvent('X'));
    timeline.addEvent(5000, new StringAppenderEvent('d'));
    timeline.addEvent(6000, new StringAppenderEvent('E'));
    timeline.playTo(10000);
    verifyAppliedEvents("AbcdE");

    timeline.jumpTo(5500);
    verifyAppliedEvents("");
  }

  @Test
  public void testAddEventWhilePlaying() {
    timeline = new NavigableGameModelTimeline();
    timeline.addEvent(1000, new StringAppenderEvent('A'));
    timeline.addEvent(1500, new StringAppenderEvent('b'));
    timeline.playTo(1500);
    verifyAppliedEvents("Ab");

    timeline.addEvent(1500, new StringAppenderEvent('c'));
    timeline.addEvent(2000, new StringAppenderEvent('d'));
    timeline.playTo(2500);
    verifyAppliedEvents("cd");
  }

  @Test(expected = IllegalArgumentException.class)
  public void testAddEventBeforeLastEvent() {
    timeline = new NavigableGameModelTimeline();
    timeline.addEvent(1000, new StringAppenderEvent('a'));
    timeline.addEvent(500, new StringAppenderEvent('b'));
  }

  @Test(expected = IllegalArgumentException.class)
  public void testAddEventBeforeCurrentTime() {
    timeline = new NavigableGameModelTimeline();
    timeline.addEvent(1000, new StringAppenderEvent('a'));
    timeline.playTo(2500);
    timeline.addEvent(2000, new StringAppenderEvent('b'));
  }
}

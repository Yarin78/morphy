package se.yarin.chess;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

public class GameHeaderModelTest {

  private GameHeaderModel header;

  @Before
  public void setup() {
    header = new GameHeaderModel();
  }

  @Test
  public void testEmptyHeader() {
    assertEquals(0, header.getAllFields().size());
    assertEquals(NAG.NONE, header.getLineEvaluation());
  }

  @Test
  public void testSetWhitePlayerName() {
    header.setField("white", "Mardell, Jimmy");
    assertEquals("Mardell, Jimmy", header.getWhite());
  }

  @Test
  public void testSetBlackElo() {
    header.setField("blackElo", 2100);
    assertEquals(2100, (int) header.getBlackElo());

    header.setBlackElo(2300);
    assertEquals(2300, (int) header.getBlackElo());

    header.setBlackElo(null);
    assertNull(header.getBlackElo());
  }

  @Test
  public void testSetResult() {
    header.setField("result", GameResult.DRAW);
    assertEquals(GameResult.DRAW, header.getResult());

    header.setResult(GameResult.WHITE_WINS_ON_FORFEIT);
    assertEquals(GameResult.WHITE_WINS_ON_FORFEIT, header.getResult());
  }

  @Test
  public void testSetDate() {
    header.setField("date", new Date(2016, 6));
    assertEquals(new Date(2016, 6), header.getDate());
  }

  @Test
  public void testSetEco() {
    header.setField("eco", new Eco("C76"));
    assertEquals(new Eco("C76"), header.getEco());
  }

  @Test
  public void testUnknownFieldIsExtraTag() {
    header.setField("WhiteFideId", "1503014");
    assertEquals("1503014", header.getField("WhiteFideId"));
    assertEquals("1503014", header.getExtraTag("WhiteFideId"));
    assertEquals(1, header.getExtraTags().size());
  }

  @Test(expected = IllegalArgumentException.class)
  public void testExtraTagMustBeString() {
    header.setField("custom", 100.0);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testExtraTagCantShadowStandardField() {
    header.setExtraTag("white", "Jimmy");
  }

  @Test
  public void testUnsetField() {
    header.setField("white", "Jimmy");
    header.setField("black", "NA");
    header.setField("Custom", "8");
    assertEquals(3, header.getAllFields().size());

    header.setField("white", null);
    assertNull(header.getWhite());
    assertEquals(2, header.getAllFields().size());

    header.unsetField("Custom");
    assertNull(header.getField("Custom"));
    assertEquals(1, header.getAllFields().size());
  }

  @Test(expected = IllegalArgumentException.class)
  public void testSetFieldWithWrongType() {
    header.setField("white", 5);
  }

  @Test
  public void testAllFieldsExcludesEntityIds() {
    header.setWhite("Carlsen, Magnus");
    header.setWhiteId(17L);
    assertEquals(1, header.getAllFields().size());
    assertEquals(17L, (long) header.getWhiteId());
  }

  @Test
  public void testClearEntityIds() {
    header.setWhite("Carlsen, Magnus");
    header.setWhiteId(17L);
    header.setEventId(3L);
    header.clearEntityIds();
    assertNull(header.getWhiteId());
    assertNull(header.getEventId());
    assertEquals("Carlsen, Magnus", header.getWhite());
  }

  @Test
  public void testClear() {
    header.setField("white", "Jimmy");
    header.setField("black", "NA");
    header.setField("Custom", "8");
    header.setWhiteId(4L);
    assertEquals(3, header.getAllFields().size());

    header.clear();
    assertEquals(0, header.getAllFields().size());
    assertNull(header.getWhite());
    assertNull(header.getWhiteId());
  }

  @Test
  public void testReplaceAll() {
    header.setField("white", "Jimmy");
    header.setField("black", "NA");
    header.setField("Custom", "8");

    GameHeaderModel newHeader = new GameHeaderModel();
    newHeader.setField("white", "Kasparov");
    newHeader.setField("WhiteTitle", "GM");
    newHeader.setWhiteId(12L);
    header.replaceAll(newHeader);
    assertEquals("Kasparov", header.getWhite());
    assertEquals("GM", header.getField("WhiteTitle"));
    assertEquals(12L, (long) header.getWhiteId());
    assertNull(header.getBlack());
    assertNull(header.getField("Custom"));
  }

  @Test
  public void testCopyAndEquals() {
    header.setWhite("Kasparov");
    header.setWhiteElo(2851);
    header.setWhiteId(12L);
    header.setExtraTag("WhiteTitle", "GM");

    GameHeaderModel copy = new GameHeaderModel(header);
    assertEquals(header, copy);
    assertEquals(header.hashCode(), copy.hashCode());

    copy.setWhiteId(13L);
    assertNotEquals(header, copy);
  }
}

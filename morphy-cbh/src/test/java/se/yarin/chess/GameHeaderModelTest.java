package se.yarin.chess;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class GameHeaderModelTest {

  private GameHeaderModel header;
  private int numFiredChanges;
  private GameHeaderModelChangeListener listener;

  @Before
  public void setup() {
    header = new GameHeaderModel();
    this.listener = (headerModel) -> numFiredChanges++;
    this.header.addChangeListener(listener);
  }

  @After
  public void tearDown() {
    this.header.removeChangeListener(listener);
  }

  @Test
  public void testEmptyHeader() {
    assertEquals(0, header.getAllFields().size());
    assertEquals(0, numFiredChanges);
  }

  @Test
  public void testSetWhitePlayerName() {
    header.setField("white", "Mardell, Jimmy");
    assertEquals("Mardell, Jimmy", header.getWhite());
    assertEquals(1, numFiredChanges);
  }

  @Test
  public void testSetBlackElo() {
    header.setField("blackElo", 2100);
    assertEquals(2100, (int) header.getBlackElo());

    header.setBlackElo(2300);
    assertEquals(2300, (int) header.getBlackElo());
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
  public void testSetCustomField() {
    header.setField("double", 100.0);
    assertEquals(100.0, header.getField("double"));
  }

  @Test
  public void testUnsetField() {
    header.setField("white", "Jimmy");
    header.setField("black", "NA");
    header.setField("custom", 8);
    assertEquals(3, header.getAllFields().size());
    assertEquals(3, numFiredChanges);

    header.setField("white", null);
    assertNull(header.getWhite());
    assertEquals(2, header.getAllFields().size());

    header.unsetField("custom");
    assertNull(header.getField("custom"));
    assertEquals(1, header.getAllFields().size());
    assertEquals(5, numFiredChanges);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testSetFieldWithWrongType() {
    header.setField("white", 5);
  }

  @Test
  public void testSetMultipleFields() {
    // include illegal
    HashMap<String, Object> map = new HashMap<>();
    map.put("white", "Jimmy");
    map.put("whiteElo", 2123);
    map.put("result", GameResult.NOT_FINISHED);
    map.put("date", 123);
    map.put("custom", "some data");
    map.put("annotator", "Kasparov");
    header.setFields(map);
    assertEquals(1, numFiredChanges);
    assertEquals("Jimmy", header.getWhite());
    assertEquals(2123, (int) header.getWhiteElo());
    assertEquals(GameResult.NOT_FINISHED, header.getResult());
    assertEquals("some data", header.getField("custom"));
    assertEquals("Kasparov", header.getAnnotator());
  }

  @Test
  public void testClear() {
    header.setField("white", "Jimmy");
    header.setField("black", "NA");
    header.setField("custom", 8);
    assertEquals(3, header.getAllFields().size());
    assertEquals("Jimmy", header.getWhite());
    assertEquals(3, numFiredChanges);

    header.clear();
    assertEquals(0, header.getAllFields().size());
    assertNull(header.getWhite());
    assertEquals(4, numFiredChanges);
  }

  @Test
  public void testReplaceAll() {
    header.setField("white", "Jimmy");
    header.setField("black", "NA");
    header.setField("custom", 8);
    assertEquals(3, numFiredChanges);

    GameHeaderModel newHeader = new GameHeaderModel();
    newHeader.setField("white", "Kasparov");
    newHeader.setField("whiteTitle", "GM");
    header.replaceAll(newHeader);
    assertEquals("Kasparov", header.getWhite());
    assertEquals("GM", header.getField("whiteTitle"));
    assertNull(header.getBlack());
    assertNull(header.getField("custom"));

    assertEquals(4, numFiredChanges);
  }
}

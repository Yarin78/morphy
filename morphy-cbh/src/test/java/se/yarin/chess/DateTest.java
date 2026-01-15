package se.yarin.chess;

import org.junit.Test;

import java.util.Calendar;

import static org.junit.Assert.assertEquals;

public class DateTest {

  @Test
  public void testFullDate() {
    assertEquals("2016.06.23", new Date(2016, 6, 23).toString());
    assertEquals("2016.12.01", new Date(2016, 12, 1).toString());
    assertEquals("0500.01.01", new Date(500, 1, 1).toString());
  }

  @Test
  public void testPartialDate() {
    assertEquals("2016.06.??", new Date(2016, 6).toString());
    assertEquals("1972.??.??", new Date(1972).toString());
    assertEquals("2014.??.12", new Date(2014, 0, 12).toString());
    assertEquals("????.??.01", new Date(0, 0, 1).toString());
  }

  @Test
  public void testToday() {
    // Technically this test could fail if very unfortunate...
    Date today = Date.today();
    assertEquals(Calendar.getInstance().get(Calendar.YEAR), today.year());
    assertEquals(Calendar.getInstance().get(Calendar.MONTH) + 1, today.month());
    assertEquals(Calendar.getInstance().get(Calendar.DAY_OF_MONTH), today.day());
  }
}

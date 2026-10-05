package se.yarin.chess;

import org.junit.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DateTest {

  @Test
  public void chronologicalOrderSortsPartialDatesBeforeFullOnes() {
    List<Date> dates = new ArrayList<>();
    // compareTo has 1990 equal to both of these, which differ; enough of them breaks a sort by it
    for (int i = 0; i < 100; i++) {
      dates.add(new Date(1990, 1 + i % 12, 1 + i % 28));
      dates.add(new Date(1990));
      dates.add(new Date(1990, 1 + (i * 7) % 12));
    }
    Collections.shuffle(dates);
    dates.sort(Date.CHRONOLOGICAL);
    for (int i = 1; i < dates.size(); i++) {
      Date a = dates.get(i - 1);
      Date b = dates.get(i);
      assertTrue(a.month() < b.month() || (a.month() == b.month() && a.day() <= b.day()));
    }
    assertEquals(new Date(1990), dates.get(0));
  }

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
    LocalDate now = LocalDate.now();
    assertEquals(now.getYear(), today.year());
    assertEquals(now.getMonthValue(), today.month());
    assertEquals(now.getDayOfMonth(), today.day());
  }
}

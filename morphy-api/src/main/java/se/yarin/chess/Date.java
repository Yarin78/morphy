package se.yarin.chess;

import java.time.LocalDate;
import java.util.Comparator;

/**
 * Represents a date where each of the year, month and day parts are optional. Date is immutable.
 */
public record Date(int year, int month, int day) implements Comparable<Date> {
  /**
   * The order to sort dates by: by year, month and day, a missing part before any given one, so
   * "1990" comes before "1990-03". Unlike {@link #compareTo}, which treats a missing part as equal
   * to any for searching, this is a total order, as sorting needs.
   */
  public static final Comparator<Date> CHRONOLOGICAL =
      Comparator.comparingInt(Date::year).thenComparingInt(Date::month).thenComparingInt(Date::day);

  public Date(int year) {
    this(year, 0, 0);
  }

  public Date(int year, int month) {
    this(year, month, 0);
  }

  public static Date unset() {
    return new Date(0, 0, 0);
  }

  public static Date today() {
    LocalDate now = LocalDate.now();
    return new Date(now.getYear(), now.getMonthValue(), now.getDayOfMonth());
  }

  public boolean isUnset() {
    return this.year == 0;
  }

  @Override
  public String toString() {
    // This is the PGN format
    StringBuilder sb = new StringBuilder();

    if (year == 0) {
      sb.append("????");
    } else {
      sb.append(String.format("%04d", year));
    }
    sb.append('.');

    if (month == 0) {
      sb.append("??");
    } else {
      sb.append(String.format("%02d", month));
    }
    sb.append('.');

    if (day == 0) {
      sb.append("??");
    } else {
      sb.append(String.format("%02d", day));
    }

    return sb.toString();
  }

  public String toPrettyString() {
    // "YYYY", "YYYY-MM" or "YYYY-MM-DD"
    if (year > 0 && month > 0 && day > 0) {
      return String.format("%04d-%02d-%02d", year, month, day);
    } else if (year > 0 && month > 0) {
      return String.format("%04d-%02d", year, month);
    } else if (year > 0) {
      return String.format("%04d", year);
    } else {
      return "";
    }
  }

  @Override
  public int compareTo(Date that) {
    // If some part of the date is missing from one side, we treat it as equal
    // This it to ensure that when searching for "play date >= 1970"
    // we will find games that say "October 1970".
    // It isn't transitive, so it can't sort dates; CHRONOLOGICAL does.

    if (this.year != that.year) {
      return this.year - that.year;
    }
    if (this.month == 0 || that.month == 0) {
      return 0;
    }
    if (this.month != that.month) {
      return this.month - that.month;
    }
    if (this.day == 0 || that.day == 0) {
      return 0;
    }
    return this.day - that.day;
  }
}

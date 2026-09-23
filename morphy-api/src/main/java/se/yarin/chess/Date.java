package se.yarin.chess;

import java.time.LocalDate;

/**
 * Represents a date where each of the year, month and day parts are optional. Date is immutable.
 */
public record Date(int year, int month, int day) implements Comparable<Date> {
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
    // Either "YYYY" or "YYYY-MM-DD"
    if (year > 0 && month > 0 && day > 0) {
      return String.format("%04d-%02d-%02d", year, month, day);
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

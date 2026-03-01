package se.yarin.morphy.queries.filter;

import java.time.YearMonth;
import org.jetbrains.annotations.NotNull;
import se.yarin.chess.Date;

/**
 * Parses partial date strings into Date ranges for filtering.
 *
 * <p>Supports:
 *
 * <ul>
 *   <li>"2020" -> entire year (2020-01-01 to 2020-12-31)
 *   <li>"2020-03" -> entire month (2020-03-01 to 2020-03-31)
 *   <li>"2020-03-15" -> exact date (2020-03-15)
 * </ul>
 */
public class PartialDateParser {

  /**
   * Represents a date range parsed from a partial date string.
   *
   * @param from start date (inclusive)
   * @param to end date (inclusive)
   */
  public record DateRange(@NotNull Date from, @NotNull Date to) {}

  /**
   * Parses a partial date string into a date range.
   *
   * @param dateStr the date string to parse (format: "YYYY", "YYYY-MM", or "YYYY-MM-DD")
   * @return the parsed date range
   * @throws IllegalArgumentException if the date string is invalid
   */
  public static @NotNull DateRange parse(@NotNull String dateStr) {
    String trimmed = dateStr.trim();
    String[] parts = trimmed.split("-");

    try {
      if (parts.length == 1) {
        // Year only: "2020" -> 2020-01-01 to 2020-12-31
        int year = Integer.parseInt(parts[0]);
        validateYear(year);
        return new DateRange(new Date(year, 1, 1), new Date(year, 12, 31));
      } else if (parts.length == 2) {
        // Year and month: "2020-03" -> 2020-03-01 to 2020-03-31
        int year = Integer.parseInt(parts[0]);
        int month = Integer.parseInt(parts[1]);
        validateYear(year);
        validateMonth(month);

        int lastDay = YearMonth.of(year, month).lengthOfMonth();
        return new DateRange(new Date(year, month, 1), new Date(year, month, lastDay));
      } else if (parts.length == 3) {
        // Full date: "2020-03-15" -> exact date
        int year = Integer.parseInt(parts[0]);
        int month = Integer.parseInt(parts[1]);
        int day = Integer.parseInt(parts[2]);
        validateYear(year);
        validateMonth(month);
        validateDay(day, year, month);

        Date date = new Date(year, month, day);
        return new DateRange(date, date);
      } else {
        throw new IllegalArgumentException(
            "Invalid date format: '"
                + dateStr
                + "'. Expected YYYY, YYYY-MM, or YYYY-MM-DD");
      }
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(
          "Invalid date: '" + dateStr + "'. Must contain numbers", e);
    }
  }

  /**
   * Parses a date range from a filter condition. Supports both the ".." operator and embedded ".."
   * in the value (e.g., "2020..2025", "2020..", "..2025").
   *
   * <p>For single dates, expands using partial date rules (e.g., "2020" -> Jan 1 to Dec 31).
   */
  public static @NotNull DateRange parseRange(@NotNull FilterCondition condition) {
    String value = condition.value();
    if ("..".equals(condition.operator()) || value.contains("..")) {
      String[] parts = value.split("\\.\\.", 2);
      Date from = parts[0].isEmpty() ? Date.unset() : parse(parts[0]).from();
      Date to =
          parts.length > 1 && !parts[1].isEmpty() ? parse(parts[1]).to() : Date.unset();
      return new DateRange(from, to);
    }
    return parse(value);
  }

  private static void validateYear(int year) {
    if (year < 1) {
      throw new IllegalArgumentException("Invalid year: " + year + ". Must be >= 1");
    }
    if (year > 9999) {
      throw new IllegalArgumentException("Invalid year: " + year + ". Must be <= 9999");
    }
  }

  private static void validateMonth(int month) {
    if (month < 1 || month > 12) {
      throw new IllegalArgumentException(
          "Invalid month: " + month + ". Must be between 1 and 12");
    }
  }

  private static void validateDay(int day, int year, int month) {
    if (day < 1 || day > 31) {
      throw new IllegalArgumentException(
          "Invalid day: " + day + ". Must be between 1 and 31");
    }
    int maxDay = YearMonth.of(year, month).lengthOfMonth();
    if (day > maxDay) {
      throw new IllegalArgumentException(
          "Invalid day: " + day + " for " + year + "-" + month + ". Maximum is " + maxDay);
    }
  }
}

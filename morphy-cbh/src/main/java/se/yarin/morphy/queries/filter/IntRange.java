package se.yarin.morphy.queries.filter;

import org.jetbrains.annotations.NotNull;

/**
 * Represents a parsed integer range from a filter condition.
 *
 * <p>Supports the following value formats:
 *
 * <ul>
 *   <li>"5..10" -> range from 5 to 10
 *   <li>"5.." -> range from 5 to defaultMax
 *   <li>"..10" -> range from 0 to 10
 *   <li>"5" -> exact match (5, 5)
 * </ul>
 *
 * Also supports the ".." operator (e.g., {@code field..5..10} or {@code field..5}).
 */
public record IntRange(int min, int max) {

  /**
   * Parses an integer range from a filter condition.
   *
   * @param condition the filter condition to parse
   * @param defaultMax the maximum value when the upper bound is omitted
   * @return the parsed integer range
   */
  public static @NotNull IntRange parse(@NotNull FilterCondition condition, int defaultMax) {
    String value = condition.value();
    if ("..".equals(condition.operator()) || value.contains("..")) {
      String[] parts = value.split("\\.\\.", 2);
      int min = parts[0].isEmpty() ? 0 : Integer.parseInt(parts[0]);
      int max =
          parts.length > 1 && !parts[1].isEmpty() ? Integer.parseInt(parts[1]) : defaultMax;
      return new IntRange(min, max);
    }
    int exact = Integer.parseInt(value);
    return new IntRange(exact, exact);
  }
}

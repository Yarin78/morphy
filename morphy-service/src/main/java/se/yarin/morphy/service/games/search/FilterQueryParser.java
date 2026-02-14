package se.yarin.morphy.service.games.search;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Parses filter query strings into structured FilterCondition objects.
 *
 * <p>Query syntax:
 *
 * <ul>
 *   <li>AND-only boolean logic: conditions separated by "AND" (case-insensitive)
 *   <li>Operators: ":" (equals), ".." (range)
 *   <li>Modifiers: comma-separated key=value pairs after the value (e.g.,
 *       "player.name:Carlsen,position=white")
 *   <li>Wildcards: "*" for ECO codes (e.g., "eco:B9*")
 * </ul>
 *
 * <p>Example queries:
 *
 * <ul>
 *   <li>"result:1-0 AND rating:2600.."
 *   <li>"player.name:Carlsen,position=white AND date:2024"
 *   <li>"eco:B9* AND tournament.name:Candidates"
 * </ul>
 */
@Component
public class FilterQueryParser {
  private static final Logger log = LoggerFactory.getLogger(FilterQueryParser.class);

  // Pattern for parsing a single condition: field:value or field..value
  // with optional modifiers like ,position=white,mode=average
  private static final Pattern CONDITION_PATTERN =
      Pattern.compile(
          "^\\s*([a-zA-Z][a-zA-Z0-9.]*)"
              + // field name (e.g., "result", "player.name")
              "\\s*(:|\\.\\.)"
              + // operator (: or ..)
              "\\s*([^,\\s]+)"
              + // value (non-comma, non-space characters)
              "((?:\\s*,\\s*[a-zA-Z][a-zA-Z0-9]*\\s*=\\s*[^,\\s]+)*)"
              + // optional modifiers
              "\\s*$");

  /**
   * Parses a filter query string into a list of FilterCondition objects.
   *
   * @param query the query string to parse
   * @return list of parsed filter conditions
   * @throws IllegalArgumentException if the query is invalid
   */
  public @NotNull List<FilterCondition> parse(@NotNull String query) {
    if (query.isBlank()) {
      return List.of();
    }

    List<FilterCondition> conditions = new ArrayList<>();

    // Split on AND (case-insensitive)
    String[] parts = query.split("\\s+AND\\s+", -1);

    for (String part : parts) {
      String trimmed = part.trim();
      if (trimmed.isEmpty()) {
        throw new IllegalArgumentException(
            "Empty condition in query (check for consecutive ANDs or leading/trailing ANDs)");
      }

      FilterCondition condition = parseCondition(trimmed);
      conditions.add(condition);
    }

    return conditions;
  }

  /**
   * Parses a single condition string.
   *
   * @param conditionStr the condition string (e.g., "result:1-0" or
   *     "player.name:Carlsen,position=white")
   * @return the parsed FilterCondition
   * @throws IllegalArgumentException if the condition is invalid
   */
  private @NotNull FilterCondition parseCondition(@NotNull String conditionStr) {
    Matcher matcher = CONDITION_PATTERN.matcher(conditionStr);
    if (!matcher.matches()) {
      throw new IllegalArgumentException(
          "Invalid filter condition: '"
              + conditionStr
              + "'. Expected format: field:value or field..value with optional modifiers like"
              + " ,key=value");
    }

    String field = matcher.group(1);
    String operator = matcher.group(2);
    String value = matcher.group(3);
    String modifiersStr = matcher.group(4);

    Map<String, String> modifiers = parseModifiers(modifiersStr);

    log.debug(
        "Parsed condition: field={}, operator={}, value={}, modifiers={}",
        field,
        operator,
        value,
        modifiers);

    return new FilterCondition(field, operator, value, modifiers);
  }

  /**
   * Parses modifiers from a string like ",position=white,mode=average".
   *
   * @param modifiersStr the modifiers string
   * @return map of modifier key-value pairs
   */
  private @NotNull Map<String, String> parseModifiers(@NotNull String modifiersStr) {
    if (modifiersStr.isBlank()) {
      return Map.of();
    }

    Map<String, String> modifiers = new HashMap<>();

    // Split on commas, skip empty strings
    String[] pairs = modifiersStr.split(",");
    for (String pair : pairs) {
      String trimmed = pair.trim();
      if (trimmed.isEmpty()) {
        continue;
      }

      String[] keyValue = trimmed.split("=", 2);
      if (keyValue.length != 2) {
        throw new IllegalArgumentException(
            "Invalid modifier: '" + trimmed + "'. Expected format: key=value");
      }

      String key = keyValue[0].trim();
      String value = keyValue[1].trim();

      if (key.isEmpty() || value.isEmpty()) {
        throw new IllegalArgumentException(
            "Invalid modifier: '" + trimmed + "'. Key and value cannot be empty");
      }

      modifiers.put(key, value);
    }

    return modifiers;
  }
}

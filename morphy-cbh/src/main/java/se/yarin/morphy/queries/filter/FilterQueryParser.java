package se.yarin.morphy.queries.filter;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parses filter query strings into structured FilterCondition objects.
 *
 * <p>Query syntax:
 *
 * <ul>
 *   <li>Conditions are separated by whitespace; "AND" (case-insensitive) is optional
 *   <li>Structured conditions: {@code field:value} or {@code field..value}
 *   <li>Bare search terms (no {@code field:}) use the configured default field
 *   <li>Modifiers: comma-separated key=value pairs after the value (e.g.,
 *       "player.name:Carlsen,position=white")
 *   <li>Wildcards: "*" for ECO codes (e.g., "eco:B9*")
 * </ul>
 *
 * <p>Example queries:
 *
 * <ul>
 *   <li>"result:1-0 rating:2600.."
 *   <li>"Carlsen date:2024" (bare term mapped to default field)
 *   <li>"result:1-0 AND player.name:Carlsen" (explicit AND also works)
 *   <li>"player.name:Carlsen,position=white AND date:2024"
 * </ul>
 */
public class FilterQueryParser {
  private static final Logger log = LoggerFactory.getLogger(FilterQueryParser.class);

  // Pattern for parsing a single condition token: field:value or field..value
  // with optional modifiers like ,position=white,mode=average
  private static final Pattern CONDITION_PATTERN =
      Pattern.compile(
          "^([a-zA-Z][a-zA-Z0-9.]*)"
              + // field name (e.g., "result", "player.name")
              "(:|\\.\\.)"
              + // operator (: or ..)
              "([^,\\s]+)"
              + // value (non-comma, non-space characters)
              "((?:,[a-zA-Z][a-zA-Z0-9]*=[^,\\s]+)*)"
              + // optional modifiers
              "$");

  private final @Nullable String defaultField;

  /** Creates a parser that requires explicit {@code field:value} syntax for all conditions. */
  public FilterQueryParser() {
    this(null);
  }

  /**
   * Creates a parser with a default field for bare search terms.
   *
   * @param defaultField the field to use when a token has no {@code field:} prefix (e.g.,
   *     "player.name"), or null to disallow bare terms
   */
  public FilterQueryParser(@Nullable String defaultField) {
    this.defaultField = defaultField;
  }

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

    // Split on whitespace
    String[] tokens = query.trim().split("\\s+");

    for (String token : tokens) {
      // Skip "AND" tokens (case-insensitive) — they are optional noise words
      if (token.equalsIgnoreCase("AND")) {
        continue;
      }

      FilterCondition condition = parseToken(token);
      conditions.add(condition);
    }

    return conditions;
  }

  /**
   * Parses a single token into a FilterCondition.
   *
   * <p>If the token matches {@code field:value} or {@code field..value}, it is parsed as a
   * structured condition. Otherwise, it is treated as a bare search term using the configured
   * default field.
   *
   * @param token a whitespace-free token (e.g., "result:1-0", "Carlsen")
   * @return the parsed FilterCondition
   * @throws IllegalArgumentException if the token is invalid and no default field is configured
   */
  private @NotNull FilterCondition parseToken(@NotNull String token) {
    Matcher matcher = CONDITION_PATTERN.matcher(token);
    if (matcher.matches()) {
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

    // Bare search term — use default field
    if (defaultField == null) {
      throw new IllegalArgumentException(
          "Invalid filter condition: '"
              + token
              + "'. Expected format: field:value or field..value");
    }

    log.debug("Bare search term '{}' mapped to default field '{}'", token, defaultField);
    return new FilterCondition(defaultField, ":", token);
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

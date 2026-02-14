package se.yarin.morphy.service.games.search;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a parsed filter condition from a query string.
 *
 * <p>Examples:
 *
 * <ul>
 *   <li>"result:1-0" → FilterCondition("result", ":", "1-0", {})
 *   <li>"rating:2600.." → FilterCondition("rating", "..", "2600", {})
 *   <li>"player.name:Carlsen,position=white" → FilterCondition("player.name", ":", "Carlsen",
 *       {position: "white"})
 *   <li>"eco:B9*" → FilterCondition("eco", ":", "B9*", {})
 * </ul>
 */
public record FilterCondition(
    @NotNull String field, // e.g., "result", "rating", "player.name", "tournament.year"
    @NotNull String operator, // e.g., ":", "..", "*"
    @NotNull String value, // e.g., "1-0", "2600", "Carlsen", "B9*"
    @NotNull Map<String, String> modifiers // e.g., {position: "white", mode: "average"}
    ) {

  public FilterCondition(@NotNull String field, @NotNull String operator, @NotNull String value) {
    this(field, operator, value, Map.of());
  }
}

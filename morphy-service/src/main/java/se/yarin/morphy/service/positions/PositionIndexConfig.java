package se.yarin.morphy.service.positions;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The definition of a position index, from {@code position-indexes.json}: which games of which
 * database it holds, and what it's called. A database can have several, with different filters.
 *
 * @param id the index's id, its key in the file
 * @param name a short name, as its pill in a board's Games pane shows it
 * @param database the id of the database whose games it holds
 * @param filter the games it holds, in the database's game filter language (as in a game search:
 *     {@code "tournament.time:normal rating:2300..,mode=both"}); blank or null for every game
 * @param path the index directory; null for the default, next to the database: {@code
 *     <database name>.<id>.positions}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PositionIndexConfig(
    @Nullable String id,
    @NotNull String name,
    @NotNull String database,
    @Nullable String filter,
    @Nullable String path) {

  /** The filter, blank for every game. */
  public @NotNull String filterOrAll() {
    return filter == null ? "" : filter.strip();
  }

  PositionIndexConfig withId(@NotNull String id) {
    return new PositionIndexConfig(id, name, database, filter, path);
  }
}

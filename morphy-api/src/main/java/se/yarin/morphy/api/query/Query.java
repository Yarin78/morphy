package se.yarin.morphy.api.query;

import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A search: which items to match, in what order, and which slice of the result to return.
 *
 * <p>The filter is free text in the filter language (see {@link FilterQueryParser}); the database
 * parses it, using the default field of the search target for bare terms. Conditions that are
 * already structured, such as typed request parameters, can be added as {@code conditions}; all
 * conditions from both sources must match.
 *
 * <p>An empty filter with no conditions matches everything, so a query is also the way to list
 * all items page by page.
 *
 * @param filter free-text filter, or null for none
 * @param conditions additional structured conditions
 * @param sort the order of the result
 * @param offset the number of matching items to skip, at least 0
 * @param limit the maximum number of items to return, at least 1
 */
public record Query(
    @Nullable String filter,
    @NotNull List<FilterCondition> conditions,
    @NotNull Sort sort,
    int offset,
    int limit) {

  public Query {
    conditions = List.copyOf(conditions);
    if (offset < 0) {
      throw new IllegalArgumentException("offset must not be negative: " + offset);
    }
    if (limit < 1) {
      throw new IllegalArgumentException("limit must be at least 1: " + limit);
    }
  }

  /** All items in natural order. */
  public static @NotNull Query all(int offset, int limit) {
    return new Query(null, List.of(), Sort.natural(), offset, limit);
  }

  public static @NotNull Query of(
      @Nullable String filter, @NotNull Sort sort, int offset, int limit) {
    return new Query(filter, List.of(), sort, offset, limit);
  }

  /** Whether the query matches everything. */
  public boolean isUnfiltered() {
    return (filter == null || filter.isBlank()) && conditions.isEmpty();
  }
}

package se.yarin.morphy.api.query;

import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * One page of a search result.
 *
 * @param items the items on this page, in result order
 * @param offset the offset of the first item
 * @param limit the requested page size
 * @param total the total number of matching items, or null if the database did not compute it
 * @param appliedFilter a readable description of the filter that was applied
 */
public record ResultPage<T>(
    @NotNull List<T> items,
    int offset,
    int limit,
    @Nullable Long total,
    @NotNull String appliedFilter) {

  public ResultPage {
    items = List.copyOf(items);
  }
}

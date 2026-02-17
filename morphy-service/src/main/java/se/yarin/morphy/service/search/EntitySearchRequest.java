package se.yarin.morphy.service.search;

import org.jetbrains.annotations.Nullable;

/**
 * Shared request model for entity search queries.
 *
 * @param filter filter expression string
 * @param offset number of items to skip (default 0)
 * @param limit maximum number of items to return (default 50)
 * @param sortBy sort field name (default "default")
 * @param order sort order: "asc" or "desc" (default "asc")
 */
public record EntitySearchRequest(
    @Nullable String filter,
    @Nullable Integer offset,
    @Nullable Integer limit,
    @Nullable String sortBy,
    @Nullable String order) {

  public EntitySearchRequest {
    if (offset == null) {
      offset = 0;
    }
    if (limit == null) {
      limit = 50;
    }
    if (sortBy == null || sortBy.isBlank()) {
      sortBy = "default";
    }
    if (order == null || order.isBlank()) {
      order = "asc";
    }
  }
}

package se.yarin.morphy.service.search;

import org.jetbrains.annotations.Nullable;

/**
 * Shared request model for entity search queries.
 *
 * @param filter filter expression string
 * @param offset number of items to skip (default 0)
 * @param limit maximum number of items to return (default 50)
 * @param sortBy sort spec: comma-separated fields with optional +/- prefix (default "default")
 */
public record EntitySearchRequest(
    @Nullable String filter,
    @Nullable Integer offset,
    @Nullable Integer limit,
    @Nullable String sortBy) {

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
  }
}

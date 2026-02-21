package se.yarin.morphy.service.search;

import org.jetbrains.annotations.Nullable;

/**
 * Shared request model for entity search queries.
 *
 * @param filter filter expression string
 * @param offset number of items to skip (default 0)
 * @param limit maximum number of items to return (default 50)
 * @param sortBy sort spec: comma-separated fields with optional +/- prefix (default "default")
 * @param debugQueryPlans include query plan debug info (default false)
 * @param debugExecuteAllPlans execute all candidate plans for comparison (default false)
 */
public record EntitySearchRequest(
    @Nullable String filter,
    @Nullable Integer offset,
    @Nullable Integer limit,
    @Nullable String sortBy,
    @Nullable Boolean debugQueryPlans,
    @Nullable Boolean debugExecuteAllPlans) {

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
    if (debugQueryPlans == null) {
      debugQueryPlans = false;
    }
    if (debugExecuteAllPlans == null) {
      debugExecuteAllPlans = false;
    }
  }
}

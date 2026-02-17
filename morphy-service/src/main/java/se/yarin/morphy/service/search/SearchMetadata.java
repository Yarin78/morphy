package se.yarin.morphy.service.search;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Metadata about a search query execution.
 *
 * @param appliedFilter canonical representation of the filter that was applied
 * @param sortBy the field used for sorting
 * @param order the sort order
 * @param executionTimeMs query execution time in milliseconds
 */
public record SearchMetadata(
    @Nullable String appliedFilter,
    @NotNull String sortBy,
    @NotNull String order,
    long executionTimeMs) {}

package se.yarin.morphy.service.search;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Metadata about a search query execution.
 *
 * @param appliedFilter canonical representation of the filter that was applied
 * @param sortBy the sort spec that was applied
 * @param executionTimeMs query execution time in milliseconds
 */
public record SearchMetadata(
    @Nullable String appliedFilter, @NotNull String sortBy, long executionTimeMs) {}

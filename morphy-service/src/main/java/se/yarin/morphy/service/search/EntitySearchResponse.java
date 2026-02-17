package se.yarin.morphy.service.search;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.service.queryplans.QueryPlanDebugInfo;

/**
 * Generic response model for entity search queries.
 *
 * @param items list of matching entities
 * @param count number of items in this response
 * @param totalCount total number of entities matching the filter
 * @param offset current offset
 * @param limit current limit
 * @param metadata query execution metadata
 * @param debugInfo query plan debug info (null if not requested)
 * @param <T> the DTO type
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EntitySearchResponse<T>(
    @NotNull List<T> items,
    int count,
    @Nullable Integer totalCount,
    int offset,
    int limit,
    @NotNull SearchMetadata metadata,
    @Nullable QueryPlanDebugInfo debugInfo) {}

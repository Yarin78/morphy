package se.yarin.morphy.service.games.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.service.queryplans.QueryPlanDebugInfo;

/**
 * Response model for game search queries.
 *
 * @param games list of matching games
 * @param count number of games in this response
 * @param totalCount total number of games matching the filter (may be expensive to compute, null
 *     if not calculated)
 * @param offset current offset
 * @param limit current limit
 * @param metadata query execution metadata
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GameSearchResponse(
    @NotNull List<GameDto> games,
    int count,
    @Nullable Integer totalCount,
    int offset,
    int limit,
    @NotNull SearchMetadata metadata,
    @Nullable QueryPlanDebugInfo debugInfo) {}

package se.yarin.morphy.service.debug;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.service.queryplans.QueryPlanDebugInfo;

/**
 * A search result together with what it takes to debug it.
 *
 * @param result the search result, exactly as the normal search endpoint returns it
 * @param plans how the query planner executed the search
 * @param raw the raw records behind each item in {@code result}, by item id
 * @param <R> the type of the search result
 */
public record DebugSearchResponse<R>(
    @NotNull R result,
    @NotNull QueryPlanDebugInfo plans,
    @NotNull Map<Long, List<RawRecordDto>> raw) {}

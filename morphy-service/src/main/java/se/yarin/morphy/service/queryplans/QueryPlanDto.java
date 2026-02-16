package se.yarin.morphy.service.queryplans;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record QueryPlanDto(
    @NotNull String label,
    @NotNull List<QueryOperatorNodeDto> nodes,
    @NotNull List<QueryPlanEdgeDto> edges,
    @NotNull QueryCostDto totalCost,
    boolean executed,
    @Nullable Integer resultCount,
    @Nullable Boolean resultsDifferFromSelected) {}

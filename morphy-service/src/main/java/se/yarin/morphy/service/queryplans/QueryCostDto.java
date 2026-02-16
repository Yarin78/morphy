package se.yarin.morphy.service.queryplans;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jetbrains.annotations.Nullable;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record QueryCostDto(
    long estimatedRows,
    long estimatedPageReads,
    long estimatedDeserializations,
    double estimatedCpuCost,
    double estimatedIOCost,
    double estimatedTotalCost,
    @Nullable Long actualRows,
    @Nullable Long actualPhysicalPageReads,
    @Nullable Long actualLogicalPageReads,
    @Nullable Long actualDeserializations,
    @Nullable Long actualWallClockTimeMs) {}

package se.yarin.morphy.service.queryplans;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jetbrains.annotations.Nullable;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OperatorCostDto(
    long estimatedRows,
    long estimatedDeserializations,
    long estimatedPageReads,
    @Nullable Long actualRows,
    @Nullable Long actualDeserializations,
    @Nullable Long actualPhysicalPageReads,
    @Nullable Long actualLogicalPageReads,
    @Nullable Boolean actualIsDuplicate) {}

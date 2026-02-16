package se.yarin.morphy.service.queryplans;

import org.jetbrains.annotations.NotNull;

public record QueryOperatorNodeDto(
    int id,
    @NotNull String name,
    @NotNull String params,
    @NotNull String type,
    boolean hasFullData,
    boolean sorted,
    @NotNull String sortOrder,
    boolean mayContainDuplicates,
    @NotNull OperatorCostDto cost) {}

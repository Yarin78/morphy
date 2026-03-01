package se.yarin.morphy.queries.operations;

import org.jetbrains.annotations.NotNull;

/** Derived move data stored as extra data on {@link QueryData} by {@link GameMovesInfoLookup}. */
public record GameMovesInfo(@NotNull String notation, int variationPly) {}

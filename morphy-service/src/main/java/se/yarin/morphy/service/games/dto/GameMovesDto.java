package se.yarin.morphy.service.games.dto;

import org.jetbrains.annotations.Nullable;

/**
 * Game moves in various formats.
 *
 * <p>Currently only PGN format is supported, but this DTO can be extended to support other formats
 * (JSON, algebraic notation, etc.) in the future.
 */
public record GameMovesDto(@Nullable String pgn) {}

package se.yarin.morphy.model;

import org.jetbrains.annotations.Nullable;

/**
 * Game moves in various formats.
 *
 * <p>Currently only PGN format is supported, but this DTO can be extended to support other formats
 * (JSON, algebraic notation, etc.) in the future.
 *
 * <p>TODO: the PGN is movetext only, without SetUp/FEN, so the start position of a game from a
 * set-up position is lost. Add a fen field, or carry a full PGN with those tags.
 */
public record GameMovesDto(@Nullable String pgn) {}

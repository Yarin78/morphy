package se.yarin.morphy.model;

import org.jetbrains.annotations.Nullable;

/**
 * The moves of a game.
 *
 * @param pgn the moves as PGN movetext, without headers
 * @param fen the start position as FEN, or null for the standard start position. The castling
 *     field is KQkq as in regular chess, also in Chess960 (see {@link GameDto#variant()}), where the
 *     start squares of the castling kings and rooks follow from the position.
 */
public record GameMovesDto(@Nullable String pgn, @Nullable String fen) {

  /** Moves from the standard start position. */
  public GameMovesDto(@Nullable String pgn) {
    this(pgn, null);
  }
}

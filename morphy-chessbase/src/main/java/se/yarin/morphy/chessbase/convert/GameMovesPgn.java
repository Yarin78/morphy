package se.yarin.morphy.chessbase.convert;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.chess.GameMovesModel;
import se.yarin.morphy.chessbase.annotations.AnnotationConverter;
import se.yarin.morphy.pgn.PgnMoves;

/**
 * The moves of a game as PGN movetext with ChessBase annotations, kept in the comments, and the FEN
 * of the start position. What one writes, the other reads back to the same moves.
 *
 * <p>This is {@link PgnMoves} with ChessBase annotations. A {@link
 * se.yarin.morphy.model.GameMovesDto} carries the annotations next to the movetext instead, see
 * {@link GameMovesDtos}.
 */
public final class GameMovesPgn {

  /** The moves with ChessBase annotations round-tripped through PGN comments. */
  private static final @NotNull PgnMoves ROUND_TRIP =
      new PgnMoves(
          AnnotationConverter.getRoundTripConverter()::convertToPgn,
          AnnotationConverter.getRoundTripConverter()::convertToChessBase);

  private GameMovesPgn() {}

  /** The moves as single-line movetext, without headers or result. */
  public static @NotNull String toPgn(@NotNull GameMovesModel moves) {
    return ROUND_TRIP.toPgn(moves);
  }

  /** The FEN of the start position, or null if it's the standard one. */
  public static @Nullable String toFen(@NotNull GameMovesModel moves) {
    return ROUND_TRIP.toFen(moves);
  }

  /**
   * Reads movetext.
   *
   * @param pgn the movetext
   * @param fen the start position, or null for the standard one
   * @param chess960 whether the game is a Chess960 game, which decides how castling in the start
   *     position is read
   * @throws IllegalArgumentException if the movetext or the FEN can't be read
   */
  public static @NotNull GameMovesModel fromPgn(
      @NotNull String pgn, @Nullable String fen, boolean chess960) {
    return ROUND_TRIP.fromPgn(pgn, fen, chess960);
  }
}

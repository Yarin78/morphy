package se.yarin.morphy.pgn;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.chess.Chess;
import se.yarin.chess.GameMovesModel;
import se.yarin.chess.annotations.AnnotationTransformer;
import se.yarin.chess.pgn.NagStyle;
import se.yarin.chess.pgn.PgnExporter;
import se.yarin.chess.pgn.PgnFormatException;
import se.yarin.chess.pgn.PgnFormatOptions;
import se.yarin.chess.pgn.PgnParser;
import se.yarin.chess.pgn.PositionState;

/**
 * The moves of a game as {@link se.yarin.morphy.model.GameMovesDto} carries them: PGN movetext and
 * the FEN of the start position. What {@link #toPgn} and {@link #toFen} write, {@link #fromPgn}
 * reads back to the same moves.
 *
 * <p>Annotations are turned into and out of PGN comments by the transformers given to the
 * constructor; a format with annotations of its own supplies the transformers that keep them, while
 * {@link #PLAIN} keeps only the plain comments and NAGs that PGN itself has.
 */
public final class PgnMoves {

  /** Moves with the annotations plain PGN has: comments and NAGs. */
  public static final @NotNull PgnMoves PLAIN = new PgnMoves(null, null);

  private final @Nullable AnnotationTransformer toPgnTransformer;
  private final @Nullable AnnotationTransformer fromPgnTransformer;

  /**
   * @param toPgnTransformer applied to the annotations of a game when writing movetext, or null
   * @param fromPgnTransformer applied to the annotations read from movetext, or null
   */
  public PgnMoves(
      @Nullable AnnotationTransformer toPgnTransformer,
      @Nullable AnnotationTransformer fromPgnTransformer) {
    this.toPgnTransformer = toPgnTransformer;
    this.fromPgnTransformer = fromPgnTransformer;
  }

  /** The moves as single-line movetext, without headers or result. */
  public @NotNull String toPgn(@NotNull GameMovesModel moves) {
    PgnExporter exporter = new PgnExporter(PgnFormatOptions.DEFAULT, toPgnTransformer);
    return exporter.exportMovesOnly(moves, NagStyle.SUFFIXES);
  }

  /** The FEN of the start position, or null if it's the standard one. */
  public @Nullable String toFen(@NotNull GameMovesModel moves) {
    if (!moves.isSetupPosition()) {
      return null;
    }
    return PositionState.toFen(moves.root().position(), moves.root().ply());
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
  public @NotNull GameMovesModel fromPgn(
      @NotNull String pgn, @Nullable String fen, boolean chess960) {
    PgnParser parser = new PgnParser(fromPgnTransformer);
    try {
      if (fen == null) {
        return parser.parseMoves(pgn);
      }
      PositionState start = PositionState.fromFen(fen, chess960);
      int startPly =
          Chess.moveNumberToPly(start.fullMoveNumber(), start.position().playerToMove());
      return parser.parseMoves(pgn, start.position(), startPly);
    } catch (PgnFormatException e) {
      throw new IllegalArgumentException("Invalid moves: " + e.getMessage(), e);
    }
  }
}

package se.yarin.morphy.convert;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.chess.Chess;
import se.yarin.chess.GameMovesModel;
import se.yarin.chess.pgn.NagStyle;
import se.yarin.chess.pgn.PgnExporter;
import se.yarin.chess.pgn.PgnFormatException;
import se.yarin.chess.pgn.PgnFormatOptions;
import se.yarin.chess.pgn.PgnParser;
import se.yarin.chess.pgn.PositionState;
import se.yarin.morphy.games.annotations.AnnotationConverter;

/**
 * The moves of a game as {@link se.yarin.morphy.model.GameMovesDto} carries them: PGN movetext and
 * the FEN of the start position, written by {@link GameDtoConverter} and read by {@link
 * GameDtoImporter}. What one writes, the other reads back to the same moves.
 */
final class GameMovesPgn {

  private GameMovesPgn() {}

  /** The moves as single-line movetext, without headers or result. */
  static @NotNull String toPgn(@NotNull GameMovesModel moves) {
    PgnExporter exporter =
        new PgnExporter(
            PgnFormatOptions.DEFAULT, AnnotationConverter.getRoundTripConverter()::convertToPgn);
    return exporter.exportMovesOnly(moves, NagStyle.SUFFIXES);
  }

  /** The FEN of the start position, or null if it's the standard one. */
  static @Nullable String toFen(@NotNull GameMovesModel moves) {
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
  static @NotNull GameMovesModel fromPgn(@NotNull String pgn, @Nullable String fen, boolean chess960) {
    PgnParser parser = new PgnParser(AnnotationConverter.getRoundTripConverter()::convertToChessBase);
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

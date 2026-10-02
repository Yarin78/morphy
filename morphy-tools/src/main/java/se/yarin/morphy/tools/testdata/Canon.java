package se.yarin.morphy.tools.testdata;

import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.chessbase.convert.GameMovesDtos;
import se.yarin.morphy.chessbase.convert.GameMovesPgn;
import se.yarin.morphy.model.GameMovesDto;

/**
 * Moves in one standard form, so that two that say the same thing compare equal.
 *
 * <p>The form is PGN movetext with the annotations in comments, as ChessBase writes them, like
 * {@code [%csl Ge4]}. What a format cannot hold, it drops from the moves it gives back, and the
 * comparison fails, which is what it should do.
 */
final class Canon {

  private Canon() {}

  /**
   * The moves in standard form.
   *
   * @return the movetext, or a note that it can't be read
   */
  static String moves(@Nullable GameMovesDto moves, boolean chess960) {
    if (moves == null || moves.pgn() == null) {
      return "";
    }
    try {
      return GameMovesPgn.toPgn(GameMovesDtos.fromDto(moves, chess960));
    } catch (RuntimeException e) {
      return "<unreadable: " + e.getMessage() + "> " + moves.pgn() + " " + moves.annotations();
    }
  }
}

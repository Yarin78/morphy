package se.yarin.morphy.tools.testdata;

import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.model.GameMovesDto;
import se.yarin.morphy.pgn.PgnMoves;

/**
 * Movetext in one standard form, so that two texts that say the same thing compare equal.
 *
 * <p>The comparison is deliberately in plain PGN whichever the format: the tags in a comment,
 * {@code [%csl Ge4]} and the like, are just text there. What a ChessBase format cannot hold, it
 * then drops from the text it gives back, and the comparison fails, which is what it should do. It
 * follows that the annotations of the test games have to be written the way the formats give them
 * back: the colours and the spelling of the tags that they keep.
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
      return PgnMoves.PLAIN.toPgn(PgnMoves.PLAIN.fromPgn(moves.pgn(), moves.fen(), chess960));
    } catch (RuntimeException e) {
      return "<unreadable: " + e.getMessage() + "> " + moves.pgn();
    }
  }
}

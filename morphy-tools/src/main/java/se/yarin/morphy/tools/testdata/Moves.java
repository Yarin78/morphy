package se.yarin.morphy.tools.testdata;

import java.util.List;
import java.util.Random;
import org.jetbrains.annotations.Nullable;
import se.yarin.chess.GameMovesModel;
import se.yarin.chess.Move;
import se.yarin.morphy.model.GameMovesDto;
import se.yarin.morphy.pgn.PgnMoves;

/** Movetext for the test games: real games, and seeded random ones. */
final class Moves {

  private Moves() {}

  /**
   * Movetext in the form the databases give it back, and checked to be legal.
   *
   * @param movetext the moves; no headers and no result
   * @param fen the start position, or null for the standard one
   * @throws IllegalArgumentException if a move is illegal
   */
  static GameMovesDto of(String movetext, @Nullable String fen, boolean chess960) {
    GameMovesModel model = PgnMoves.PLAIN.fromPgn(movetext, fen, chess960);
    return new GameMovesDto(PgnMoves.PLAIN.toPgn(model), PgnMoves.PLAIN.toFen(model));
  }

  static GameMovesDto of(String movetext) {
    return of(movetext, null, false);
  }

  /** A game with no moves at all. */
  static GameMovesDto none() {
    return of("");
  }

  /**
   * A random sequence of legal moves from the standard start, that ends early only if the game
   * does.
   */
  static GameMovesDto random(Random random, int plies) {
    GameMovesModel model = new GameMovesModel();
    GameMovesModel.Node current = model.root();
    for (int i = 0; i < plies; i++) {
      List<Move> moves = current.position().generateAllLegalMoves();
      if (moves.isEmpty()) {
        break;
      }
      current = current.addMove(moves.get(random.nextInt(moves.size())));
    }
    return new GameMovesDto(PgnMoves.PLAIN.toPgn(model), null);
  }
}

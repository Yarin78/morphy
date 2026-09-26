package se.yarin.morphy.tools.testdata;

import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import org.jetbrains.annotations.Nullable;
import se.yarin.chess.GameMovesModel;
import se.yarin.chess.Move;
import se.yarin.chess.annotations.Annotation;
import se.yarin.morphy.chessbase.convert.GameMovesPgn;
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

  /**
   * A random sequence of legal moves from a start position.
   *
   * @param fen the start position
   */
  static GameMovesDto randomFrom(Random random, String fen, boolean chess960, int plies) {
    GameMovesModel model = PgnMoves.PLAIN.fromPgn("", fen, chess960);
    GameMovesModel.Node current = model.root();
    for (int i = 0; i < plies; i++) {
      List<Move> moves = current.position().generateAllLegalMoves();
      if (moves.isEmpty()) {
        break;
      }
      current = current.addMove(moves.get(random.nextInt(moves.size())));
    }
    return new GameMovesDto(PgnMoves.PLAIN.toPgn(model), PgnMoves.PLAIN.toFen(model));
  }

  /** A random game with random variations added at random positions. */
  static GameMovesDto randomWithVariations(Random random, int plies, int variationMoves) {
    GameMovesModel model = new GameMovesModel();
    GameMovesModel.Node current = model.root();
    for (int i = 0; i < plies; i++) {
      List<Move> moves = current.position().generateAllLegalMoves();
      if (moves.isEmpty()) {
        break;
      }
      current = current.addMove(moves.get(random.nextInt(moves.size())));
    }
    List<GameMovesModel.Node> nodes = model.getAllNodes();
    for (int i = 0; i < variationMoves; i++) {
      GameMovesModel.Node node = nodes.get(random.nextInt(nodes.size()));
      List<Move> moves = node.position().generateAllLegalMoves();
      if (!moves.isEmpty()) {
        nodes.add(node.addMove(moves.get(random.nextInt(moves.size()))));
      }
    }
    return new GameMovesDto(PgnMoves.PLAIN.toPgn(model), null);
  }

  /**
   * The kinds of annotation that the ChessBase formats find in movetext, such as {@code
   * GraphicalSquares} and {@code TimeSpent}. A tag that is spelled wrong or has no meaning is left
   * as ordinary text in a comment, and so is not among them.
   */
  static Set<String> annotationTypes(String movetext, @Nullable String fen, boolean chess960) {
    GameMovesModel model = GameMovesPgn.fromPgn(movetext, fen, chess960);
    Set<String> types = new TreeSet<>();
    for (GameMovesModel.Node node : model.getAllNodes()) {
      for (Annotation annotation : node.getAnnotations()) {
        types.add(
            annotation
                .getClass()
                .getSimpleName()
                .replaceFirst("^Immutable", "")
                .replaceFirst("Annotation$", ""));
      }
    }
    return types;
  }

  /**
   * Movetext with annotations, checked to hold the kinds of annotation it is meant to.
   *
   * @param expected the kinds that must be found, see {@link #annotationTypes}
   * @throws IllegalStateException if one is missing, which means a tag isn't understood
   */
  static GameMovesDto annotated(String movetext, String... expected) {
    return annotated(movetext, null, false, expected);
  }

  static GameMovesDto annotated(
      String movetext, @Nullable String fen, boolean chess960, String... expected) {
    Set<String> found = annotationTypes(movetext, fen, chess960);
    for (String type : expected) {
      if (!found.contains(type)) {
        throw new IllegalStateException(
            "No " + type + " annotation found in the movetext, only " + found + ": " + movetext);
      }
    }
    // The text has to be in the form the ChessBase formats write it in, or the comparison of what
    // they give back would have to allow for differences, and could then miss what is lost
    GameMovesDto plain = of(movetext, fen, chess960);
    GameMovesModel model = GameMovesPgn.fromPgn(movetext, fen, chess960);
    String written = GameMovesPgn.toPgn(model);
    GameMovesDto normal = of(written, fen, chess960);
    if (!plain.pgn().equals(normal.pgn())) {
      throw new IllegalStateException(
          "The annotations are not in the form ChessBase writes them in.\n  given:   " + plain.pgn()
              + "\n  written: " + normal.pgn());
    }
    return plain;
  }
}

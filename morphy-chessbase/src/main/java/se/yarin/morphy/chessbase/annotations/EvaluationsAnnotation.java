package se.yarin.morphy.chessbase.annotations;

import java.util.List;
import org.immutables.value.Value;
import se.yarin.chess.annotations.Annotation;

/**
 * Engine evaluations of the positions of the main line, on the game as a whole: one for the
 * position before the first move, and one after each move. They are not rewritten when moves are
 * added afterwards, so there may be fewer than there are moves.
 */
@Value.Immutable
public abstract class EvaluationsAnnotation extends Annotation {

  /**
   * The evaluation of a position.
   *
   * @param eval the evaluation in centipawns from White's side, or the moves to mate
   * @param depth the search depth
   * @param type 0 an ordinary evaluation, 1 a mate, 0xff none (eval and depth 0); 2 and 0x20 also
   *     occur and are unknown
   */
  public record Evaluation(int eval, int depth, int type) {}

  @Value.Parameter
  public abstract List<Evaluation> evaluations();

  @Override
  public String toString() {
    return "EvaluationsAnnotation: " + evaluations().size() + " positions";
  }
}

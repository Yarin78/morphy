package se.yarin.morphy.cli.update;

import se.yarin.chess.GameModel;

/**
 * A single transformation applied to a game as part of a {@code morphy update} run. Multiple
 * updaters can be composed and applied to the same game in sequence.
 */
public interface GameUpdater {
  /**
   * Applies this update to {@code model}, mutating it in place.
   *
   * @return true if the update applies and the game should be kept; false if the game should be
   *     skipped entirely (e.g. it didn't match an opening classification)
   */
  boolean apply(GameModel model);
}

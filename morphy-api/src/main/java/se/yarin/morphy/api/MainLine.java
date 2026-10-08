package se.yarin.morphy.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.chess.GameMovesModel;
import se.yarin.chess.Move;
import se.yarin.chess.MoveCode;
import se.yarin.chess.Player;
import se.yarin.chess.Position;

/**
 * A game's main line, played through one position at a time: a format can decode each move only
 * when it's asked for, so a game is only decoded as far as it's played through.
 */
public interface MainLine {

  /** The current position: the game's start position at first. */
  @NotNull
  Position position();

  /** The move played from the current position, or null if the game ends there. */
  @Nullable
  Move move();

  /** Plays {@link #move()}, which must not be null, making the position after it the current. */
  void advance();

  /**
   * The hash of the current position, {@link Position#getZobristHashLo()}; a format may work it
   * out without making the position.
   */
  default long hash() {
    return position().getZobristHashLo();
  }

  /** Whether White is to move in the current position. */
  default boolean whiteToMove() {
    return position().playerToMove() == Player.WHITE;
  }

  /**
   * The move played from the current position as a {@link MoveCode}, or {@link MoveCode#NONE} if
   * the game ends there; a format may work it out without making the move.
   */
  default int moveCode() {
    Move move = move();
    return move == null ? MoveCode.NONE : MoveCode.of(move);
  }

  /** The main line of a decoded game. */
  static @NotNull MainLine of(@NotNull GameMovesModel moves) {
    return new MainLine() {
      private GameMovesModel.Node node = moves.root();

      @Override
      public @NotNull Position position() {
        return node.position();
      }

      @Override
      public @Nullable Move move() {
        return node.hasMoves() ? node.mainNode().lastMove() : null;
      }

      @Override
      public void advance() {
        node = node.mainNode();
      }
    };
  }
}

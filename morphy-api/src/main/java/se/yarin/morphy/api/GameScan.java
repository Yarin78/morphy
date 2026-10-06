package se.yarin.morphy.api;

import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An open scan of a database's games, see {@link GameScanning}. Games can be read from several
 * threads at once.
 */
public interface GameScan extends AutoCloseable {

  /** The highest game id; ids run from 1. */
  int maxId();

  /**
   * Reads a game's moves and facts.
   *
   * @return the game, or null if the id holds no regular chess game: a guiding text, an analysis,
   *     a deleted or Chess960 game, or one whose moves can't be decoded
   */
  @Nullable
  ScannedGame read(int id);

  /**
   * Reads every game, each passed to the consumer, on several threads at once, in no particular
   * order; returns when all are done. Records that hold no regular chess game are left out, as
   * {@link #read} returns null for them. A format can read its files in large pieces here, which
   * is much faster than game by game.
   *
   * @param consumer called from several threads at once
   */
  default void forEach(@NotNull Consumer<ScannedGame> consumer) {
    ParallelBatches.run(
        maxId(),
        256,
        (first, end) -> {
          for (int id = first; id < end; id++) {
            ScannedGame game = read(id);
            if (game != null) {
              consumer.accept(game);
            }
          }
        });
  }

  @Override
  void close();
}

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

  /** What's done with each game's main line by {@link #forEachMainLine}. */
  @FunctionalInterface
  interface MainLineVisitor {
    /**
     * Plays through as much of a game's main line as is wanted.
     *
     * @param id the game id
     * @param facts the game's header facts
     * @param line the main line, at the game's start position
     */
    void visit(int id, @NotNull GameFacts facts, @NotNull MainLine line);
  }

  /**
   * Plays through the main line of every game, on several threads at once, in no particular order;
   * returns when all are done. The games are those {@link #forEach} gives. A format can decode
   * each game's moves only as far as the visitor plays through them, which is faster when only
   * the main line, or only its beginning, is wanted.
   *
   * @param visitor called from several threads at once
   */
  default void forEachMainLine(@NotNull MainLineVisitor visitor) {
    forEach(game -> visitor.visit(game.id(), game.facts(), MainLine.of(game.moves())));
  }

  @Override
  void close();
}

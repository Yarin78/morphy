package se.yarin.morphy.api;

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
   * returns when all are done. The games are those {@link #read} gives: records that hold no
   * regular chess game are left out. By default each game is read and decoded in full; a format can
   * read its files in large pieces instead, and decode each game's moves only as far as the visitor
   * plays through them.
   *
   * @param visitor called from several threads at once
   */
  default void forEachMainLine(@NotNull MainLineVisitor visitor) {
    ParallelBatches.run(
        maxId(),
        256,
        (first, end) -> {
          for (int id = first; id < end; id++) {
            ScannedGame game = read(id);
            if (game != null) {
              visitor.visit(game.id(), game.facts(), MainLine.of(game.moves()));
            }
          }
        });
  }

  @Override
  void close();
}

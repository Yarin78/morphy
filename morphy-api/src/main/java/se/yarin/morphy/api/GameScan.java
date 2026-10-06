package se.yarin.morphy.api;

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

  @Override
  void close();
}

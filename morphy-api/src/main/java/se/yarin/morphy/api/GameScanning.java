package se.yarin.morphy.api;

import org.jetbrains.annotations.NotNull;

/**
 * Reading every game's moves and the few header facts needed to index them, as fast as the format
 * allows: an extension of a {@link Database}, reached through {@link Database#extension}. Used to
 * build a database's position index.
 */
public interface GameScanning {

  /**
   * Starts a scan of every game; the database can't be changed until it's closed, which must be
   * done by the thread that started it.
   */
  default @NotNull GameScan openScan() {
    return openScan("");
  }

  /**
   * Starts a scan of the games matching a filter, as {@link #openScan()} does. The filter is in the
   * database's game filter language, as in {@link Database#findGames}: {@code
   * "tournament.time:normal rating:2400..,mode=both"}. A format checks it on the game headers (and
   * the entities they refer to) before reading any moves, so a scan of few games is fast.
   *
   * @param filter the filter; blank for every game
   * @throws IllegalArgumentException if the filter is invalid
   */
  @NotNull
  GameScan openScan(@NotNull String filter);
}

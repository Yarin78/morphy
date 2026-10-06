package se.yarin.morphy.api;

import org.jetbrains.annotations.NotNull;

/**
 * Reading every game's moves and the few header facts needed to index them, as fast as the format
 * allows: an extension of a {@link Database}, reached through {@link Database#extension}. Used to
 * build a database's position index.
 */
public interface GameScanning {

  /**
   * Starts a scan; the database can't be changed until it's closed, which must be done by the
   * thread that started it.
   */
  @NotNull
  GameScan openScan();
}

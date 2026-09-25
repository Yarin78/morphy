package se.yarin.morphy.games.filters;

import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.games.ExtendedGameHeader;
import se.yarin.morphy.games.GameHeader;
import se.yarin.morphy.storage.ItemStorageFilter;

/**
 * A filter on games, applied to the game header and/or the extended game header when games are
 * scanned.
 */
public interface GameFilter {
  /**
   * The part of the filter that applies to the game header, or null if there is none. Deliberately
   * not defaulted: a filter that implements {@link ItemStorageFilter} for the game header but
   * doesn't return itself here is silently ignored by every scan.
   */
  @Nullable
  ItemStorageFilter<GameHeader> gameHeaderFilter();

  /** The part of the filter that applies to the extended game header, or null if there is none. */

  default @Nullable ItemStorageFilter<ExtendedGameHeader> extendedGameHeaderFilter() {
    return null;
  }

  static GameFilter of(
      @Nullable ItemStorageFilter<GameHeader> gameHeaderFilter,
      @Nullable ItemStorageFilter<ExtendedGameHeader> extendedGameHeaderFilter) {
    return new GameFilter() {
      @Override
      public @Nullable ItemStorageFilter<GameHeader> gameHeaderFilter() {
        return gameHeaderFilter;
      }

      @Override
      public @Nullable ItemStorageFilter<ExtendedGameHeader> extendedGameHeaderFilter() {
        return extendedGameHeaderFilter;
      }
    };
  }
}

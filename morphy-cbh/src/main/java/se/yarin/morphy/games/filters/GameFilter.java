package se.yarin.morphy.games.filters;

import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.games.ExtendedGameHeader;
import se.yarin.morphy.games.GameHeader;
import se.yarin.morphy.storage.ItemStorageFilter;

public interface GameFilter {
  default @Nullable ItemStorageFilter<GameHeader> gameHeaderFilter() {
    return null;
  }

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

package se.yarin.morphy.search;

import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.games.ExtendedGameHeader;
import se.yarin.morphy.games.GameHeader;
import se.yarin.morphy.storage.ItemStorageFilter;

public interface GameIteratorFilter {
    default @Nullable ItemStorageFilter<GameHeader> gameHeaderFilter() { return null; }
    default @Nullable ItemStorageFilter<ExtendedGameHeader> extendedGameHeaderFilter() { return null; }
}

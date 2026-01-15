package se.yarin.morphy.storage;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.games.GameHeader;

import java.nio.ByteBuffer;

public interface ItemStorageFilter<TItem> {
  boolean matches(int id, @NotNull TItem item);

  default boolean matchesSerialized(int id, @NotNull ByteBuffer buf) {
    return true;
  }
}

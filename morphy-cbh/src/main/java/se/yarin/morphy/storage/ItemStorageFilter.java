package se.yarin.morphy.storage;

import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;
import se.yarin.morphy.IdObject;

public interface ItemStorageFilter<TItem extends IdObject> {
  boolean matches(@NotNull TItem item);

  default boolean matchesSerialized(@NotNull ByteBuffer buf) {
    return true;
  }
}

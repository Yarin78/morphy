package se.yarin.morphy.games.filters;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.games.GameHeader;
import se.yarin.morphy.storage.ItemStorageFilter;
import se.yarin.util.ByteBufferUtil;

import java.nio.ByteBuffer;

public class IsGameFilter implements ItemStorageFilter<GameHeader>, GameFilter {
  @Override
  public boolean matches(int id, @NotNull GameHeader gameHeader) {
    return !gameHeader.guidingText();
  }

  @Override
  public boolean matchesSerialized(int id, @NotNull ByteBuffer buf) {
    return isGame(buf);
  }

  static boolean isGame(@NotNull ByteBuffer buf) {
    return (ByteBufferUtil.getUnsignedByte(buf, 0) & 2) == 0;
  }

  public @Nullable ItemStorageFilter<GameHeader> gameHeaderFilter() {
    return this;
  }

  @Override
  public String toString() {
    return "isGame";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    return o != null && getClass() == o.getClass();
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}

package se.yarin.morphy.games.filters;

import java.nio.ByteBuffer;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.games.GameHeader;
import se.yarin.morphy.games.TopGamesStorage;
import se.yarin.morphy.storage.ItemStorageFilter;

public class TopGameFilter implements ItemStorageFilter<GameHeader>, GameFilter {
  private final @NotNull TopGamesStorage topGamesStorage;
  private final boolean expected;

  public TopGameFilter(@NotNull TopGamesStorage topGamesStorage, boolean expected) {
    this.topGamesStorage = topGamesStorage;
    this.expected = expected;
  }

  @Override
  public boolean matches(int id, @NotNull GameHeader gameHeader) {
    return topGamesStorage.isTopGame(id) == expected;
  }

  @Override
  public boolean matchesSerialized(int id, @NotNull ByteBuffer buf) {
    return topGamesStorage.isTopGame(id) == expected;
  }

  @Override
  public @Nullable ItemStorageFilter<GameHeader> gameHeaderFilter() {
    return this;
  }

  @Override
  public String toString() {
    return "topgame:" + expected;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    TopGameFilter that = (TopGameFilter) o;
    return expected == that.expected;
  }

  @Override
  public int hashCode() {
    return Objects.hash(expected);
  }
}

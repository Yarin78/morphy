package se.yarin.morphy.games.filters;

import java.nio.ByteBuffer;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.games.GameHeader;
import se.yarin.morphy.games.Medal;
import se.yarin.morphy.storage.ItemStorageFilter;
import se.yarin.util.ByteBufferUtil;

public class MedalFilter implements ItemStorageFilter<GameHeader>, GameFilter {

  private final @Nullable Medal specificMedal;

  /** Creates a filter that matches games with any medal. */
  public MedalFilter() {
    this.specificMedal = null;
  }

  /** Creates a filter that matches games with a specific medal. */
  public MedalFilter(@NotNull Medal specificMedal) {
    this.specificMedal = specificMedal;
  }

  @Override
  public boolean matches(int id, @NotNull GameHeader gameHeader) {
    if (specificMedal != null) {
      return gameHeader.medals().contains(specificMedal);
    }
    return !gameHeader.medals().isEmpty();
  }

  @Override
  public boolean matchesSerialized(int id, @NotNull ByteBuffer buf) {
    int medalBits = ByteBufferUtil.getUnsignedShortB(buf, 37);
    if (specificMedal != null) {
      return (medalBits & (1 << specificMedal.ordinal())) != 0;
    }
    return medalBits != 0;
  }

  @Override
  public @Nullable ItemStorageFilter<GameHeader> gameHeaderFilter() {
    return this;
  }

  @Override
  public String toString() {
    if (specificMedal != null) {
      return "medal:" + specificMedal.name();
    }
    return "medals:true";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    MedalFilter that = (MedalFilter) o;
    return specificMedal == that.specificMedal;
  }

  @Override
  public int hashCode() {
    return Objects.hash(specificMedal);
  }
}

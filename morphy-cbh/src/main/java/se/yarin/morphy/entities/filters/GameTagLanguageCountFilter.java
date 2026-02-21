package se.yarin.morphy.entities.filters;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.entities.GameTag;

public class GameTagLanguageCountFilter implements EntityFilter<GameTag> {
  private final int min;
  private final int max;

  public GameTagLanguageCountFilter(int min, int max) {
    this.min = min;
    this.max = max;
  }

  @Override
  public boolean matches(@NotNull GameTag gameTag) {
    int count = gameTag.languageCount();
    return count >= min && count <= max;
  }

  @Override
  public String toString() {
    if (min == 0) {
      return "languageCount <= " + max;
    } else if (max == Integer.MAX_VALUE) {
      return "languageCount >= " + min;
    } else if (min == max) {
      return "languageCount = " + min;
    } else {
      return "languageCount >= " + min + " and languageCount <= " + max;
    }
  }

  @Override
  public EntityType entityType() {
    return EntityType.GAME_TAG;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    GameTagLanguageCountFilter that = (GameTagLanguageCountFilter) o;
    return min == that.min && max == that.max;
  }

  @Override
  public int hashCode() {
    return Objects.hash(min, max);
  }
}

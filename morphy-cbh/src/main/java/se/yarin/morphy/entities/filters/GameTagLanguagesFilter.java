package se.yarin.morphy.entities.filters;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.entities.GameTag;

public class GameTagLanguagesFilter implements EntityFilter<GameTag> {
  private final @NotNull String language;

  public GameTagLanguagesFilter(@NotNull String language) {
    this.language = language.toUpperCase();
  }

  @Override
  public boolean matches(@NotNull GameTag gameTag) {
    return gameTag.languages().contains(language);
  }

  @Override
  public String toString() {
    return "languages contains '%s'".formatted(language);
  }

  @Override
  public EntityType entityType() {
    return EntityType.GAME_TAG;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    GameTagLanguagesFilter that = (GameTagLanguagesFilter) o;
    return language.equals(that.language);
  }

  @Override
  public int hashCode() {
    return Objects.hash(language);
  }
}

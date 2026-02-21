package se.yarin.morphy.entities.filters;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.entities.GameTag;

public class GameTagTitleFilter implements EntityIndexFilter<GameTag> {
  private final @NotNull String title;
  private final boolean caseSensitive;
  private final boolean exactMatch;
  private final boolean useCalculatedTitle;

  public GameTagTitleFilter(
      @NotNull String title, boolean caseSensitive, boolean exactMatch) {
    this(title, caseSensitive, exactMatch, false);
  }

  public GameTagTitleFilter(
      @NotNull String title,
      boolean caseSensitive,
      boolean exactMatch,
      boolean useCalculatedTitle) {
    this.title = caseSensitive ? title : title.toLowerCase();
    this.caseSensitive = caseSensitive;
    this.exactMatch = exactMatch;
    this.useCalculatedTitle = useCalculatedTitle;
  }

  private boolean matches(@NotNull String value) {
    if (exactMatch) {
      return caseSensitive ? value.equals(title) : value.equalsIgnoreCase(title);
    }
    return caseSensitive
        ? value.startsWith(title)
        : value.toLowerCase().startsWith(title);
  }

  @Override
  public boolean matches(@NotNull GameTag gameTag) {
    return matches(useCalculatedTitle ? gameTag.title() : gameTag.englishTitle());
  }

  @Override
  public String toString() {
    String field = useCalculatedTitle ? "title" : "englishTitle";
    String titleStr = caseSensitive ? field : "lower(%s)".formatted(field);

    if (exactMatch) {
      return "%s='%s'".formatted(titleStr, title);
    } else {
      return "%s like '%s%%'".formatted(titleStr, title);
    }
  }

  @Override
  public EntityType entityType() {
    return EntityType.GAME_TAG;
  }

  @Override
  public @Nullable GameTag start() {
    return caseSensitive && !useCalculatedTitle ? GameTag.of(title) : null;
  }

  @Override
  public @Nullable GameTag end() {
    return caseSensitive && !useCalculatedTitle ? GameTag.of(title + "zzz") : null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    GameTagTitleFilter that = (GameTagTitleFilter) o;
    return caseSensitive == that.caseSensitive
        && exactMatch == that.exactMatch
        && useCalculatedTitle == that.useCalculatedTitle
        && title.equals(that.title);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(title, caseSensitive, exactMatch, useCalculatedTitle);
  }
}

package se.yarin.morphy.entities.filters;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.entities.Source;

public class SourceTitleFilter implements EntityIndexFilter<Source> {
  private final @NotNull String title;
  private final boolean caseSensitive;
  private final boolean exactMatch;

  public SourceTitleFilter(@NotNull String title, boolean caseSensitive, boolean exactMatch) {
    this.title = caseSensitive ? title : title.toLowerCase();
    this.caseSensitive = caseSensitive;
    this.exactMatch = exactMatch;
  }

  private boolean matches(@NotNull String sourceTitle) {
    if (exactMatch) {
      return caseSensitive ? sourceTitle.equals(title) : sourceTitle.equalsIgnoreCase(title);
    }
    return caseSensitive
        ? sourceTitle.startsWith(title)
        : sourceTitle.toLowerCase().startsWith(title);
  }

  @Override
  public boolean matches(@NotNull Source source) {
    return matches(source.title());
  }

  @Override
  public String toString() {
    String titleStr = caseSensitive ? "title" : "lower(title)";

    if (exactMatch) {
      return "%s='%s'".formatted(titleStr, title);
    } else {
      return "%s like '%s%%'".formatted(titleStr, title);
    }
  }

  @Override
  public EntityType entityType() {
    return EntityType.SOURCE;
  }

  @Override
  public @Nullable Source start() {
    return caseSensitive ? Source.of(title) : null;
  }

  @Override
  public @Nullable Source end() {
    return caseSensitive ? Source.of(title + "zzz") : null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SourceTitleFilter that = (SourceTitleFilter) o;
    return caseSensitive == that.caseSensitive
        && exactMatch == that.exactMatch
        && title.equals(that.title);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(title, caseSensitive, exactMatch);
  }
}

package se.yarin.morphy.entities.filters;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.entities.Annotator;
import se.yarin.morphy.entities.EntityType;

public class AnnotatorNameFilter implements EntityFilter<Annotator> {
  private final @NotNull String name;
  private final boolean caseSensitive;
  private final boolean exactMatch;

  public AnnotatorNameFilter(@NotNull String name, boolean caseSensitive, boolean exactMatch) {
    this.name = caseSensitive ? name : name.toLowerCase();
    this.caseSensitive = caseSensitive;
    this.exactMatch = exactMatch;
  }

  private boolean matches(@NotNull String annotatorName) {
    if (exactMatch) {
      return caseSensitive ? annotatorName.equals(name) : annotatorName.equalsIgnoreCase(name);
    }
    return caseSensitive
        ? annotatorName.startsWith(name)
        : annotatorName.toLowerCase().startsWith(name);
  }

  @Override
  public boolean matches(@NotNull Annotator annotator) {
    return matches(annotator.name());
  }

  @Override
  public String toString() {
    String nameStr = caseSensitive ? "name" : "lower(name)";

    if (exactMatch) {
      return "%s='%s'".formatted(nameStr, name);
    } else {
      return "%s like '%s%%'".formatted(nameStr, name);
    }
  }

  @Override
  public EntityType entityType() {
    return EntityType.ANNOTATOR;
  }
}

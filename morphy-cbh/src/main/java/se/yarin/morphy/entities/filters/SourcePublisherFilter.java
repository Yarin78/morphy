package se.yarin.morphy.entities.filters;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.entities.Source;

public class SourcePublisherFilter implements EntityFilter<Source> {
  private final @NotNull String publisher;
  private final boolean caseSensitive;
  private final boolean exactMatch;

  public SourcePublisherFilter(
      @NotNull String publisher, boolean caseSensitive, boolean exactMatch) {
    this.publisher = caseSensitive ? publisher : publisher.toLowerCase();
    this.caseSensitive = caseSensitive;
    this.exactMatch = exactMatch;
  }

  private boolean matches(@NotNull String sourcePublisher) {
    if (exactMatch) {
      return caseSensitive
          ? sourcePublisher.equals(publisher)
          : sourcePublisher.equalsIgnoreCase(publisher);
    }
    return caseSensitive
        ? sourcePublisher.startsWith(publisher)
        : sourcePublisher.toLowerCase().startsWith(publisher);
  }

  @Override
  public boolean matches(@NotNull Source source) {
    return matches(source.publisher());
  }

  @Override
  public String toString() {
    String field = caseSensitive ? "publisher" : "lower(publisher)";

    if (exactMatch) {
      return "%s='%s'".formatted(field, publisher);
    } else {
      return "%s like '%s%%'".formatted(field, publisher);
    }
  }

  @Override
  public EntityType entityType() {
    return EntityType.SOURCE;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SourcePublisherFilter that = (SourcePublisherFilter) o;
    return caseSensitive == that.caseSensitive
        && exactMatch == that.exactMatch
        && publisher.equals(that.publisher);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(publisher, caseSensitive, exactMatch);
  }
}

package se.yarin.morphy.entities.filters;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.entities.Tournament;

import java.util.List;

/**
 * Filter that matches tournaments by multiple titles using OR logic.
 * This is useful for searching for games in any of several tournaments.
 */
public class MultiTournamentTitleFilter implements EntityFilter<Tournament> {
  private final @NotNull List<TournamentTitleFilter> filters;

  /**
   * Creates a filter that matches tournaments whose title matches any of the given titles.
   *
   * @param titles the tournament titles to match
   * @param caseSensitive whether the search should be case-sensitive
   * @param exactMatch whether to match the exact title or just the prefix
   */
  public MultiTournamentTitleFilter(
      @NotNull List<String> titles, boolean caseSensitive, boolean exactMatch) {
    this.filters =
        titles.stream()
            .map(title -> new TournamentTitleFilter(title, caseSensitive, exactMatch))
            .toList();
  }

  @Override
  public boolean matches(@NotNull Tournament tournament) {
    return filters.stream().anyMatch(filter -> filter.matches(tournament));
  }

  @Override
  public boolean matchesSerialized(byte[] serializedItem) {
    return filters.stream().anyMatch(filter -> filter.matchesSerialized(serializedItem));
  }

  @Override
  public EntityType entityType() {
    return EntityType.TOURNAMENT;
  }

  @Override
  public String toString() {
    if (filters.size() == 1) {
      return filters.get(0).toString();
    }
    return filters.stream()
        .map(f -> "(" + f.toString() + ")")
        .reduce((a, b) -> a + " or " + b)
        .orElse("");
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    MultiTournamentTitleFilter that = (MultiTournamentTitleFilter) o;
    return filters.equals(that.filters);
  }

  @Override
  public int hashCode() {
    return filters.hashCode();
  }
}

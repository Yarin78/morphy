package se.yarin.morphy.entities.filters;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.entities.Player;

import java.util.List;

/**
 * Filter that matches players by multiple names using OR logic.
 * This is useful for searching for games involving any of several players.
 */
public class MultiPlayerNameFilter implements EntityFilter<Player> {
  private final @NotNull List<PlayerNameFilter> filters;

  /**
   * Creates a filter that matches players whose names match any of the given names.
   *
   * @param names the player names to match (can include comma or space separation)
   * @param caseSensitive whether the search should be case-sensitive
   * @param exactMatch whether to match the exact name or just the prefix
   */
  public MultiPlayerNameFilter(
      @NotNull List<String> names, boolean caseSensitive, boolean exactMatch) {
    this.filters =
        names.stream()
            .map(name -> new PlayerNameFilter(name, caseSensitive, exactMatch))
            .toList();
  }

  @Override
  public boolean matches(@NotNull Player player) {
    return filters.stream().anyMatch(filter -> filter.matches(player));
  }

  @Override
  public EntityType entityType() {
    return EntityType.PLAYER;
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
    MultiPlayerNameFilter that = (MultiPlayerNameFilter) o;
    return filters.equals(that.filters);
  }

  @Override
  public int hashCode() {
    return filters.hashCode();
  }
}

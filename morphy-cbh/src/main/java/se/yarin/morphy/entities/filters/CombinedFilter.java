package se.yarin.morphy.entities.filters;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.IdObject;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.games.filters.CombinedGameFilter;
import se.yarin.morphy.games.filters.GameFilter;
import se.yarin.morphy.queries.QueryPlanner;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.stream.Collectors;

public class CombinedFilter<T extends IdObject> implements EntityFilter<T> {
  private final @NotNull List<EntityFilter<T>> filters;
  private final @NotNull EntityType entityType;

  public static <T extends IdObject> @Nullable EntityFilter<T> combine(@NotNull List<EntityFilter<T>> filters) {
    if (filters.size() == 0) {
      return null;
    }
    if (filters.size() == 1) {
      return filters.get(0);
    }
    return new CombinedFilter<>(filters);
  }

  public CombinedFilter(@NotNull List<EntityFilter<T>> filters) {
    if (filters.size() == 0) {
      throw new IllegalArgumentException("Must contain at least one filter");
    }

    this.filters = List.copyOf(filters);
    this.entityType = filters.get(0).entityType();

    if (!filters.stream().allMatch(filter -> filter.entityType().equals(this.entityType))) {
      throw new IllegalArgumentException("Can't combine entity filters of different types");
    }
  }

  @Override
  public boolean matches(@NotNull T item) {
    return filters.stream().allMatch(filter -> filter.matches(item));
  }

  @Override
  public boolean matchesSerialized(@NotNull ByteBuffer buf) {
    return filters.stream().allMatch(filter -> filter.matchesSerialized(buf));
  }

  @Override
  public String toString() {
    return filters.stream().map(Object::toString).collect(Collectors.joining(" and "));
  }

  @Override
  public double expectedMatch(@NotNull QueryPlanner planner) {
    // Since filters are likely not independent, don't combine the ratios (by multiplication),
    // but instead pick the single best filter as an estimate expected match
    return filters.stream().mapToDouble(filter -> filter.expectedMatch(planner)).min().orElse(1.0);
  }

  @Override
  public EntityType entityType() {
    return this.entityType;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    CombinedFilter<?> that = (CombinedFilter<?>) o;
    return filters.equals(that.filters) && entityType == that.entityType;
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(filters, entityType);
  }
}

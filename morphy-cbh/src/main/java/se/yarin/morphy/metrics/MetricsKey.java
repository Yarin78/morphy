package se.yarin.morphy.metrics;

import org.jetbrains.annotations.NotNull;

public record MetricsKey(@NotNull String group, @NotNull String name) implements Comparable<MetricsKey> {
  @Override
  public String toString() {
    return "{%s.%s}".formatted(group, name);
  }

  @Override
  public int compareTo(@NotNull MetricsKey o) {
    int groupDiff = group.compareTo(o.group);
    if (groupDiff != 0) {
      return groupDiff;
    }
    return name.compareTo(o.name);
  }
}

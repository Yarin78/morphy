package se.yarin.morphy.metrics;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class MetricsKey implements Comparable<MetricsKey> {
    private @NotNull String group;
    private @NotNull String name;

    public MetricsKey(@NotNull String group, @NotNull String name) {
        this.group = group;
        this.name = name;
    }

    public @NotNull String group() {
        return group;
    }

    public @NotNull String name() {
        return name;
    }

    @Override
    public String toString() {
        return "{%s.%s}".formatted(group, name);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MetricsKey that = (MetricsKey) o;
        return group.equals(that.group) && name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(group, name);
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

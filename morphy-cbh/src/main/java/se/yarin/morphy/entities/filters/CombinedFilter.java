package se.yarin.morphy.entities.filters;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.queries.QueryPlanner;

import java.util.List;
import java.util.stream.Collectors;

public class CombinedFilter<T> implements EntityFilter<T> {
    private final @NotNull List<EntityFilter<T>> filters;

    public CombinedFilter(@NotNull List<EntityFilter<T>> filters) {
        this.filters = List.copyOf(filters);
    }

    @Override
    public boolean matches(@NotNull T item) {
        return filters.stream().allMatch(filter -> filter.matches(item));
    }

    @Override
    public boolean matchesSerialized(byte[] serializedItem) {
        return filters.stream().allMatch(filter -> filter.matchesSerialized(serializedItem));
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
}

package se.yarin.morphy.queries;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.entities.Player;
import se.yarin.morphy.entities.filters.EntityFilter;

import java.util.List;

public interface SourceQuery<T> {
    boolean isOptional();

    @NotNull List<?> filtersCovered();
}

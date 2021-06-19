package se.yarin.morphy.metrics;

import org.jetbrains.annotations.NotNull;

public interface Metrics {

    void merge(@NotNull Metrics metrics);

    void clear();

    String formatHeaderRow();

    String formatTableRow();

    default boolean isEmpty() {
        return isEmpty(0);
    }

    boolean isEmpty(int threshold);
}

package se.yarin.morphy.qqueries;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public record QueryResult<T>(
    /**
     * Total number of hits in the result. If a limit was specified and countAll was false,
     * this might be fewer than the actual total.
     */
    int total,
    /** Number of hits that were consumed by a consumer */
    int consumed,
    /**
     * All matching hits if no Consumer was specified
     * @throws IllegalStateException if the hits were consumed by a Consumer
     */
    @NotNull List<T> result,
    /** Time in millisecond it took to execute the query. */
    long elapsedTime) {
}

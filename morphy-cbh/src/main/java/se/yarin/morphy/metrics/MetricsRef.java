package se.yarin.morphy.metrics;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.Instrumentation;

import java.util.function.Consumer;

public class MetricsRef<T extends Metrics> {
    private final @NotNull Instrumentation instrumentation;
    private final @NotNull MetricsKey metricsKey;

    public MetricsRef(@NotNull Instrumentation instrumentation, @NotNull MetricsKey metricsKey) {
        this.instrumentation = instrumentation;
        this.metricsKey = metricsKey;
    }

    public @NotNull MetricsKey metricsKey() {
        return metricsKey;
    }

    public void update(Consumer<T> metricUpdater) {
        metricUpdater.accept(get());
    }

    /**
     * Gets an instance of the current in-context metrics
     * @return an instance of this metric
     */
    public T get() {
        return instrumentation.getCurrent().getMetrics(metricsKey);
    }

    /**
     * Gets an instance of the metric in a specific metrics repository
     * @return an instance of this metric
     */
    public T get(@NotNull MetricsRepository metricsRepository) {
        return metricsRepository.getMetrics(metricsKey);
    }
}

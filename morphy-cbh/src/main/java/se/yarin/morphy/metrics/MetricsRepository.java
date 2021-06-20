package se.yarin.morphy.metrics;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.Instrumentation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class MetricsRepository implements AutoCloseable {
    private final @Nullable MetricsRepository parent;
    private final @NotNull String contextName;
    private final boolean mergeOnPop;
    private final HashMap<MetricsKey, Metrics> metrics = new HashMap<>();

    protected MetricsRepository(@NotNull String contextName) {
        this.contextName = contextName;
        this.parent = null;
        this.mergeOnPop = false;
    }

    public MetricsRepository(
            @NotNull String contextName,
            @NotNull MetricsRepository parent,
            @NotNull HashMap<MetricsKey, Supplier<? extends Metrics>> registeredMetrics,
            boolean mergeOnPop) {
        this.contextName = contextName;
        this.parent = parent;
        this.mergeOnPop = mergeOnPop;
        for (Map.Entry<MetricsKey, Supplier<? extends Metrics>> entry : registeredMetrics.entrySet()) {
            metrics.put(entry.getKey(), entry.getValue().get());
        }
    }

    public @Nullable MetricsRepository parent() {
        return parent;
    }

    public @NotNull String contextName() {
        return contextName;
    }

    public int size() {
        return metrics.size();
    }

    public boolean mergeOnPop() {
        return mergeOnPop;
    }

    @Override
    public void close() {
        MetricsRepository current = this;
        while (current.parent != null) {
            current = current.parent;
        }
        if (current instanceof Instrumentation) {
            ((Instrumentation) current).popContext();
        } else {
            throw new IllegalStateException("The source of a metrics repository should be an Instrumentation");
        }
    }

    protected <T extends Metrics> void addMetric(@NotNull MetricsKey metricsKey, @NotNull T metric) {
        metrics.put(metricsKey, metric);
    }

    public @NotNull <T extends Metrics> T getMetrics(@NotNull String group, @NotNull String name) {
        return getMetrics(new MetricsKey(group, name));
    }

    public @NotNull <T extends Metrics> T getMetrics(@NotNull MetricsKey metricsKey) {
        Metrics metrics = this.metrics.get(metricsKey);
        if (metrics == null) {
            throw new IllegalArgumentException("No such metrics: " + metricsKey);
        }
        return (T) metrics; // TODO: test when using wrong type
    }

    public <T extends Metrics> Map<MetricsKey, T> getMetricsByType(@NotNull Class<T> clazz) {
        HashMap<MetricsKey, T> result = new HashMap<>();
        for (Map.Entry<MetricsKey, Metrics> entry : metrics.entrySet()) {
            if (entry.getValue().getClass() == clazz) {
                result.put(entry.getKey(), (T) entry.getValue());
            }
        }
        return result;
    }

    public boolean exists(@NotNull MetricsKey metricsKey) {
        return this.metrics.containsKey(metricsKey);
    }

    public void merge(@NotNull MetricsRepository subMetrics) {
        for (Map.Entry<MetricsKey, Metrics> entry : subMetrics.metrics.entrySet()) {
            Metrics metrics = this.metrics.get(entry.getKey());
            if (metrics != null) {
                metrics.merge(entry.getValue());
            }
        }
    }

    public synchronized void clear() {
        for (Metrics value : metrics.values()) {
            value.clear();
        }
    }

    public synchronized void show() {
        show(0);
    }

    public synchronized void show(int min) {
        String lastGroup = null;

        for (MetricsKey metricsKey : metrics.keySet().stream().sorted().collect(Collectors.toList())) {
            Metrics metrics = this.metrics.get(metricsKey);
            if (!metrics.isEmpty(min)) {
                if (!metricsKey.group().equals(lastGroup)) {
                    System.err.println();
                    System.err.println(metrics.formatHeaderRow());
                    lastGroup = metricsKey.group();
                }
                System.err.println(metrics.formatTableRow());
            }
        }
    }
}

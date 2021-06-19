package se.yarin.morphy;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.metrics.Metrics;
import se.yarin.morphy.metrics.MetricsKey;
import se.yarin.morphy.metrics.MetricsRef;
import se.yarin.morphy.metrics.MetricsRepository;

import java.util.HashMap;
import java.util.function.Supplier;

public class Instrumentation extends MetricsRepository {
    private final ThreadLocal<MetricsRepository> nestedMetricsRepositories = ThreadLocal.withInitial(() -> this);

    private final HashMap<MetricsKey, Supplier<? extends Metrics>> registeredMetrics = new HashMap<>();

    public Instrumentation() {
        super("Global");
    }

    public synchronized <T extends Metrics> MetricsRef<T> register(@NotNull String group, @NotNull String name, @NotNull Supplier<T> metricsFactory) {
        return register(group, name, metricsFactory, false);
    }

    public synchronized <T extends Metrics> MetricsRef<T> register(@NotNull String group, @NotNull String name, @NotNull Supplier<T> metricsFactory, boolean allowReregister) {
        return register(new MetricsKey(group, name), metricsFactory, allowReregister);
    }

    public synchronized <T extends Metrics> MetricsRef<T> register(@NotNull MetricsKey metricsKey, @NotNull Supplier<T> metricsFactory, boolean allowReregister) {
        if (registeredMetrics.containsKey(metricsKey)) {
            if (allowReregister) {
                return new MetricsRef<>(this, metricsKey);
            }
            throw new IllegalArgumentException("Metrics with key " + metricsKey + " has already been registered");
        }
        registeredMetrics.put(metricsKey, metricsFactory);
        addMetric(metricsKey, metricsFactory.get());

        return new MetricsRef<>(this, metricsKey);
    }

    public synchronized @NotNull MetricsRepository pushContext(@NotNull String contextName) {
        return pushContext(contextName, false);
    }

    public synchronized @NotNull MetricsRepository pushContext(@NotNull String contextName, boolean mergeOnPop) {
        MetricsRepository nestedMetrics = new MetricsRepository(contextName, getCurrent(), registeredMetrics, mergeOnPop);
        nestedMetricsRepositories.set(nestedMetrics);
        return nestedMetrics;
    }

    public synchronized @NotNull MetricsRepository popContext() {
        MetricsRepository nestedMetrics = getCurrent();
        MetricsRepository parent = nestedMetrics.parent();
        if (parent == null) {
            throw new IllegalStateException("Not in a nested metrics context");
        }
        if (nestedMetrics.mergeOnPop()) {
            parent.merge(nestedMetrics);
        }
        nestedMetricsRepositories.set(parent);
        return nestedMetrics;
    }

    public @NotNull MetricsRepository getCurrent() {
        return nestedMetricsRepositories.get();
    }
}

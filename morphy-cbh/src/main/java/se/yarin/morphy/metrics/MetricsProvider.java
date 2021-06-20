package se.yarin.morphy.metrics;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface MetricsProvider {
    @NotNull List<MetricsKey> getMetricsKeys();
}

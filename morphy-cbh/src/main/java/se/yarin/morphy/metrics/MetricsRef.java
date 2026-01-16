package se.yarin.morphy.metrics;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.Instrumentation;

import java.util.function.Consumer;

public record MetricsRef<T extends Metrics>(
    @NotNull Instrumentation instrumentation, @NotNull MetricsKey metricsKey) {
  public void update(Consumer<T> metricUpdater) {
    metricUpdater.accept(get());
  }

  /**
   * Gets an instance of the current in-context metrics
   *
   * @return an instance of this metric
   */
  public T get() {
    return instrumentation.getCurrent().getMetrics(metricsKey);
  }

  /**
   * Gets an instance of the metric in a specific metrics repository
   *
   * @return an instance of this metric
   */
  public T get(@NotNull MetricsRepository metricsRepository) {
    return metricsRepository.getMetrics(metricsKey);
  }
}

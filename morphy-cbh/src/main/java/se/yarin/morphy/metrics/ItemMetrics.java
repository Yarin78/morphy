package se.yarin.morphy.metrics;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.Instrumentation;

public class ItemMetrics implements Metrics {
  private final @NotNull String name;

  private int gets;
  private int getRaws;
  private int puts;
  private int deserializations;
  private int serializations;

  public ItemMetrics(@NotNull String name) {
    this.name = name;
  }

  public static MetricsRef<ItemMetrics> register(Instrumentation instrumentation, String name) {
    return instrumentation.register("items", name, () -> new ItemMetrics(name), true);
  }

  public static @NotNull ItemMetrics get(
      @NotNull MetricsRepository metricsRepository, @NotNull String name) {
    return metricsRepository.getMetrics("items", name);
  }

  public void addDeserialization(int count) {
    deserializations += count;
  }

  public void addSerialization(int count) {
    serializations += count;
  }

  public void addGet(int count) {
    gets += count;
  }

  public void addPut(int count) {
    puts += count;
  }

  public void addGetRaw(int count) {
    getRaws += count;
  }

  public int deserializations() {
    return deserializations;
  }

  public int serializations() {
    return serializations;
  }

  public int gets() {
    return gets;
  }

  public int puts() {
    return puts;
  }

  public int getGetRaws() {
    return getRaws;
  }

  public void clear() {
    gets = 0;
    getRaws = 0;
    puts = 0;
    deserializations = 0;
    serializations = 0;
  }

  @Override
  public void merge(@NotNull Metrics metrics) {
    ItemMetrics other = (ItemMetrics) metrics;

    gets += other.gets;
    getRaws += other.getRaws;
    puts += other.puts;
    deserializations += other.deserializations;
    serializations += other.serializations;
  }

  @Override
  public String formatHeaderRow() {
    return """
                Item                  get       put     deser       ser    \s
                ---------------------------------------------------------""";
  }

  @Override
  public String formatTableRow() {
    return String.format(
        "%-15s %9d %9d %9d %9d", name, gets + getRaws, puts, deserializations, serializations);
  }

  public boolean isEmpty(int threshold) {
    return gets + getRaws <= threshold
        && puts <= threshold
        && deserializations <= threshold
        && serializations <= threshold;
  }
}

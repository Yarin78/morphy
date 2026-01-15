package se.yarin.morphy.metrics;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.Instrumentation;

import java.nio.file.Path;

public class FileMetrics implements Metrics {
  private final @NotNull String fileName;

  private int physicalPageReads;
  private int logicalPageReads;
  private int pageWrites;

  public FileMetrics(@NotNull String fileName) {
    this.fileName = fileName;
  }

  public static MetricsRef<FileMetrics> register(
      @NotNull Instrumentation instrumentation, @NotNull Path path) {
    String fileName = path.toString();
    int extensionStart = fileName.lastIndexOf(".");
    if (extensionStart < 0) {
      throw new IllegalArgumentException("The file must have an extension");
    }
    return register(instrumentation, fileName.substring(extensionStart + 1));
  }

  public static MetricsRef<FileMetrics> register(
      @NotNull Instrumentation instrumentation, @NotNull String name) {
    return instrumentation.register("files", name, () -> new FileMetrics(name));
  }

  public void addPhysicalReads(int count) {
    physicalPageReads += count;
  }

  public void addLogicalReads(int count) {
    logicalPageReads += count;
  }

  public void addWrites(int count) {
    pageWrites += count;
  }

  public int physicalPageReads() {
    return physicalPageReads;
  }

  public int logicalPageReads() {
    return logicalPageReads;
  }

  public int pageWrites() {
    return pageWrites;
  }

  public void clear() {
    physicalPageReads = 0;
    logicalPageReads = 0;
    pageWrites = 0;
  }

  @Override
  public void merge(@NotNull Metrics metrics) {
    FileMetrics other = (FileMetrics) metrics;

    physicalPageReads += other.physicalPageReads;
    logicalPageReads += other.logicalPageReads;
    pageWrites += other.pageWrites;
  }

  @Override
  public String formatHeaderRow() {
    return """
                File       phyrd   logrd    wrts    \s
                -------------------------------------""";
  }

  @Override
  public String formatTableRow() {
    return String.format(
        "%-8s %7d %7d %7d", fileName, physicalPageReads, logicalPageReads, pageWrites);
  }

  @Override
  public boolean isEmpty(int threshold) {
    return physicalPageReads <= threshold
        && logicalPageReads <= threshold
        && pageWrites <= threshold;
  }
}

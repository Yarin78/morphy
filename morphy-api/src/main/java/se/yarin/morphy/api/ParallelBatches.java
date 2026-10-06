package se.yarin.morphy.api;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.jetbrains.annotations.NotNull;

/** Runs work over the ids 1 to a maximum in batches of consecutive ids, on several threads. */
public final class ParallelBatches {
  private ParallelBatches() {}

  /** The work on one batch of ids. */
  @FunctionalInterface
  public interface Batch {
    /** Works on the ids from {@code first} up to, not including, {@code end}. */
    void run(int first, int end);
  }

  /**
   * Runs the work on every batch, a thread per processor taking the next batch as it's done with
   * one; returns when all are done.
   *
   * @throws RuntimeException the first failure of a batch, after the other threads are stopped
   */
  public static void run(int maxId, int batchSize, @NotNull Batch batch) {
    int threads = Runtime.getRuntime().availableProcessors();
    AtomicInteger next = new AtomicInteger(1);
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    try {
      List<Future<?>> workers = new ArrayList<>();
      for (int t = 0; t < threads; t++) {
        workers.add(
            pool.submit(
                () -> {
                  int first;
                  while ((first = next.getAndAdd(batchSize)) <= maxId) {
                    batch.run(first, Math.min(first + batchSize, maxId + 1));
                  }
                }));
      }
      for (Future<?> worker : workers) {
        worker.get();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted", e);
    } catch (ExecutionException e) {
      if (e.getCause() instanceof RuntimeException r) {
        throw r;
      }
      throw new IllegalStateException(e.getCause());
    } finally {
      pool.shutdownNow();
    }
  }
}

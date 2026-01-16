package se.yarin.morphy.qqueries;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.DatabaseReadTransaction;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class QueryExecutor<T> {
  private final @NotNull DatabaseReadTransaction transaction;
  private final @Nullable Consumer<T> progressUpdater;

  public QueryExecutor(@NotNull DatabaseReadTransaction transaction) {
    this(transaction, null);
  }

  public QueryExecutor(
      @NotNull DatabaseReadTransaction transaction, @Nullable Consumer<T> progressUpdater) {
    this.transaction = transaction;
    this.progressUpdater = progressUpdater;
  }

  /**
   * Executes a query
   *
   * @return the query result
   */
  public QueryResult<T> execute(@NotNull ItemQuery<T> query) {
    return execute(query, 0, false);
  }

  /**
   * Executes a query
   *
   * @param limit maximum number of hits to return, or 0 if all hits should be returned.
   * @param countAll if true, even though not all hits are returned, all hits will be counted.
   * @return the query result
   */
  public QueryResult<T> execute(@NotNull ItemQuery<T> query, int limit, boolean countAll) {
    AtomicInteger hitsFound = new AtomicInteger(0);
    long startTime = System.currentTimeMillis();

    Stream<T> searchStream = query.stream(transaction);
    if (!countAll && limit > 0) {
      searchStream = searchStream.limit(limit);
    }

    ArrayList<T> result = new ArrayList<>();
    searchStream.forEachOrdered(
        item -> {
          int hits = hitsFound.incrementAndGet();
          if (hits <= limit || limit == 0) {
            result.add(item);
          }
          if (progressUpdater != null) {
            progressUpdater.accept(item);
          }
        });

    return new QueryResult<>(
        hitsFound.get(),
        0,
        result,
        System.currentTimeMillis() - startTime);
  }

  /**
   * Executes a query and passes all hits to a Consumer
   *
   * @param limit maximum number of hits to consume, or 0 if all hits should be consumed.
   * @param countAll if true, also count the total number of hits
   * @param consumer the consumer of each hit
   * @return a summary of the query result
   */
  public QueryResult<T> execute(
      @NotNull ItemQuery<T> query, int limit, boolean countAll, @NotNull Consumer<T> consumer) {
    AtomicInteger hitsFound = new AtomicInteger(0), hitsConsumed = new AtomicInteger(0);
    long startTime = System.currentTimeMillis();

    Stream<T> searchStream = query.stream(transaction);
    if (!countAll && limit > 0) {
      searchStream = searchStream.limit(limit);
    }

    searchStream.forEachOrdered(
        item -> {
          int hits = hitsFound.incrementAndGet();
          if (hits <= limit || limit == 0) {
            hitsConsumed.incrementAndGet();
            consumer.accept(item);
          }
          if (progressUpdater != null) {
            progressUpdater.accept(item);
          }
        });
    return new QueryResult<>(
        hitsFound.get(),
        hitsConsumed.get(),
        List.of(),
        System.currentTimeMillis() - startTime);
  }
}

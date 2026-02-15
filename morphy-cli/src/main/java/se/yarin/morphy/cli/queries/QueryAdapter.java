package se.yarin.morphy.cli.queries;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.IdObject;
import se.yarin.morphy.queries.operations.QueryData;
import se.yarin.morphy.queries.operations.QueryOperator;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Adapter that bridges the new query system (QueryOperator) with the legacy API (QueryResult).
 * This allows CLI commands to continue using the QueryResult interface while migrating to the new
 * query system.
 */
public class QueryAdapter {

  /**
   * Executes a query and returns results in a list.
   *
   * @param queryPlan the query operator to execute
   * @param limit maximum number of results to return (0 = no limit)
   * @param countAll if true, count all results even if limit is reached
   * @param progressUpdater optional callback for progress updates
   * @return query result with total count, consumed count, result list, and elapsed time
   */
  public static <T extends IdObject> QueryResult<T> execute(
      @NotNull QueryOperator<T> queryPlan,
      int limit,
      boolean countAll,
      @Nullable Consumer<T> progressUpdater) {
    return execute(queryPlan, limit, countAll, null, progressUpdater);
  }

  /**
   * Executes a query and passes results to a consumer.
   *
   * @param queryPlan the query operator to execute
   * @param limit maximum number of results to consume (0 = no limit)
   * @param countAll if true, count all results even if limit is reached
   * @param consumer optional consumer to receive results
   * @param progressUpdater optional callback for progress updates
   * @return query result with total count, consumed count, empty result list, and elapsed time
   */
  public static <T extends IdObject> QueryResult<T> execute(
      @NotNull QueryOperator<T> queryPlan,
      int limit,
      boolean countAll,
      @Nullable Consumer<T> consumer,
      @Nullable Consumer<T> progressUpdater) {

    long startTime = System.currentTimeMillis();
    List<T> results = new ArrayList<>();
    int consumed = 0;
    int total = 0;

    Stream<QueryData<T>> stream = queryPlan.stream();

    // Add progress updater if provided
    if (progressUpdater != null) {
      stream = stream.peek(qd -> {
        if (qd.data() != null) {
          progressUpdater.accept(qd.data());
        }
      });
    }

    // Process the stream
    boolean hasLimit = limit > 0;
    for (var qd : (Iterable<QueryData<T>>) stream::iterator) {
      T item = qd.data();
      if (item == null) {
        // Skip items without full data (shouldn't happen with proper query setup)
        continue;
      }

      total++;

      // Apply limit
      if (hasLimit && consumed >= limit) {
        if (!countAll) {
          break;
        }
        // Continue counting but don't consume
        continue;
      }

      consumed++;

      // Either add to results or pass to consumer
      if (consumer != null) {
        consumer.accept(item);
      } else {
        results.add(item);
      }
    }

    long elapsedTime = System.currentTimeMillis() - startTime;

    // If consumer was used, return empty list
    if (consumer != null) {
      return new QueryResult<>(total, consumed, List.of(), elapsedTime);
    }

    return new QueryResult<>(total, consumed, results, elapsedTime);
  }
}

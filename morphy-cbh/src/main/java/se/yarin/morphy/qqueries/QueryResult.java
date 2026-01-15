package se.yarin.morphy.qqueries;

import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@Value.Immutable
public interface QueryResult<T> {
  /**
   * @return Total number of hits in the result. If a limit was specified and countAll was false,
   *     this might be fewer than the actual total.
   */
  int total();

  /**
   * @return Number of hits that were consumed by a consumer
   */
  int consumed();

  /**
   * @return All matching hits if no Consumer was specified
   * @throws IllegalStateException if the hits were consumed by a Consumer
   */
  @NotNull
  List<T> result();

  /**
   * @return Time in millisecond it took to execute the query.
   */
  long elapsedTime();
}

package se.yarin.morphy.cli.queries;

import se.yarin.morphy.api.Database;
import se.yarin.morphy.api.query.Query;
import se.yarin.morphy.api.query.ResultPage;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Pages through a {@link Database#findGames}/{@link Database#findEntities} search, honoring an
 * optional result limit, and reports the true total the database found.
 */
public final class FacadeQuerySupport {
  private FacadeQuerySupport() {}

  private static final int PAGE_SIZE = 500;

  public record Result(long total, long consumed) {}

  /**
   * @param baseQuery the filter and sort order to search with; its own offset/limit are ignored
   * @param limit the maximum number of items to consume, or 0 for no limit
   * @param fetch fetches one page for a given offset/limit
   * @param consumer invoked once per matching item, in result order
   */
  public static <T> Result stream(
      Query baseQuery,
      int limit,
      Function<Query, ResultPage<T>> fetch,
      Consumer<T> consumer) {
    int offset = 0;
    long consumed = 0;
    Long total = null;
    while (limit <= 0 || consumed < limit) {
      int pageSize = limit > 0 ? (int) Math.min(PAGE_SIZE, limit - consumed) : PAGE_SIZE;
      Query pageQuery =
          new Query(baseQuery.filter(), baseQuery.conditions(), baseQuery.sort(), offset, pageSize);
      ResultPage<T> page = fetch.apply(pageQuery);
      if (total == null) {
        total = page.total();
      }
      for (T item : page.items()) {
        consumer.accept(item);
        consumed++;
      }
      if (page.items().size() < pageSize) {
        break;
      }
      offset += pageSize;
    }
    return new Result(total == null ? consumed : total, consumed);
  }
}

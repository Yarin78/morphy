package se.yarin.morphy.api.query;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

/**
 * The parts of running a {@link Query} that do not depend on the database format: parsing its
 * filter, paging the result and describing what was applied.
 */
public final class QuerySupport {

  private QuerySupport() {}

  /** The query's free-text filter, parsed with the target's default field, plus its conditions. */
  public static @NotNull List<FilterCondition> conditions(
      @NotNull Query query, @NotNull String defaultField) {
    List<FilterCondition> conditions = new ArrayList<>();
    if (query.filter() != null && !query.filter().isBlank()) {
      conditions.addAll(new FilterQueryParser(defaultField).parse(query.filter()));
    }
    conditions.addAll(query.conditions());
    return conditions;
  }

  /** The window of the items that the query asks for. */
  public static <T> @NotNull List<T> slice(@NotNull List<T> items, @NotNull Query query) {
    int from = Math.min(query.offset(), items.size());
    int to = Math.min(query.offset() + query.limit(), items.size());
    return items.subList(from, to);
  }

  /** A readable form of the applied conditions, e.g. {@code result:1-0 AND rating..2600..}. */
  public static @NotNull String describe(@NotNull List<FilterCondition> conditions) {
    return conditions.stream()
        .map(
            c -> {
              String s = c.field() + c.operator() + c.value();
              if (!c.modifiers().isEmpty()) {
                s +=
                    c.modifiers().entrySet().stream()
                        .map(e -> "," + e.getKey() + "=" + e.getValue())
                        .collect(Collectors.joining());
              }
              return s;
            })
        .collect(Collectors.joining(" AND "));
  }

  /** Whether the sort is the natural order, or by id alone. */
  public static boolean isIdOrder(@NotNull Sort sort) {
    return sort.isNatural()
        || (sort.keys().size() == 1 && sort.keys().getFirst().field().equalsIgnoreCase("id"));
  }
}

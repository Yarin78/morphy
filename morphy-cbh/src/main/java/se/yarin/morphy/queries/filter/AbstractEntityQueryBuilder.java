package se.yarin.morphy.queries.filter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.Database;
import se.yarin.morphy.entities.Entity;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.entities.filters.EntityFilter;
import se.yarin.morphy.queries.EntityQuery;

/**
 * Base class for entity query builders that parse filter expression strings into {@link
 * EntityQuery} instances.
 *
 * <p>Subclasses provide the filter map via {@link #filters()} and entity metadata via the
 * constructor. The base class handles parsing, dispatch, and error messages with available fields.
 */
public abstract class AbstractEntityQueryBuilder<T extends Entity> {

  private final EntityType entityType;
  private final String entityLabel;
  private final FilterQueryParser filterQueryParser;

  protected AbstractEntityQueryBuilder(
      @NotNull EntityType entityType,
      @NotNull String entityLabel,
      @NotNull String defaultField) {
    this.entityType = entityType;
    this.entityLabel = entityLabel;
    this.filterQueryParser = new FilterQueryParser(defaultField);
  }

  protected abstract Map<String, Function<FilterCondition, EntityFilter<T>>> filters();

  public @NotNull EntityQuery<T> buildQuery(
      @NotNull Database database, @Nullable String filterExpression) {
    if (filterExpression == null || filterExpression.isBlank()) {
      return new EntityQuery<>(database, entityType, List.of());
    }
    List<FilterCondition> conditions = filterQueryParser.parse(filterExpression);
    return buildQuery(database, conditions);
  }

  public @NotNull EntityQuery<T> buildQuery(
      @NotNull Database database, @NotNull List<FilterCondition> conditions) {
    List<EntityFilter<T>> filters = new ArrayList<>();
    for (FilterCondition condition : conditions) {
      filters.add(buildFilter(condition));
    }
    return new EntityQuery<>(database, entityType, List.copyOf(filters));
  }

  private @NotNull EntityFilter<T> buildFilter(@NotNull FilterCondition condition) {
    var builder = filters().get(condition.field().toLowerCase());
    if (builder == null) {
      throw new IllegalArgumentException(
          "Unknown "
              + entityLabel
              + " filter field: '"
              + condition.field()
              + "'. Available fields: "
              + String.join(", ", new TreeSet<>(filters().keySet())));
    }
    return builder.apply(condition);
  }
}

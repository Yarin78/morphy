package se.yarin.morphy.queries.filter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.Database;
import se.yarin.morphy.entities.Entity;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.entities.filters.EntityFilter;
import se.yarin.morphy.queries.EntityQuery;
import se.yarin.morphy.queries.QuerySortField;
import se.yarin.morphy.queries.QuerySortOrder;

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
  private final String defaultField;
  private final FilterQueryParser filterQueryParser;

  protected AbstractEntityQueryBuilder(
      @NotNull EntityType entityType,
      @NotNull String entityLabel,
      @NotNull String defaultField) {
    this.entityType = entityType;
    this.entityLabel = entityLabel;
    this.defaultField = defaultField;
    this.filterQueryParser = new FilterQueryParser(defaultField);
  }

  /** Returns the default field used when no field name is specified in a filter expression. */
  public @NotNull String defaultField() {
    return defaultField;
  }

  protected abstract Map<String, Function<FilterCondition, EntityFilter<T>>> filters();

  protected abstract Map<String, QuerySortField<T>> sortFields();

  protected abstract QuerySortOrder<T> defaultSortOrder();

  public @NotNull Set<String> availableSortFields() {
    return sortFields().keySet();
  }

  public @NotNull QuerySortOrder<T> buildSortOrder(@NotNull String sortSpec) {
    if (sortSpec.isBlank() || sortSpec.equalsIgnoreCase("default")) {
      return defaultSortOrder();
    }

    String[] parts = sortSpec.split(",");
    List<QuerySortField<T>> fields = new ArrayList<>();
    List<QuerySortOrder.Direction> directions = new ArrayList<>();

    for (String part : parts) {
      part = part.trim();
      if (part.isEmpty()) continue;

      QuerySortOrder.Direction explicitDir = null;
      if (part.startsWith("-")) {
        explicitDir = QuerySortOrder.Direction.DESCENDING;
        part = part.substring(1);
      } else if (part.startsWith("+")) {
        explicitDir = QuerySortOrder.Direction.ASCENDING;
        part = part.substring(1);
      }

      String fieldName = part.toLowerCase();
      QuerySortField<T> field;
      if (fieldName.equals("id")) {
        field = QuerySortField.id();
      } else {
        field = sortFields().get(fieldName);
        if (field == null) {
          throw new IllegalArgumentException(
              "Unknown sort field: '"
                  + part
                  + "'. Available fields: id, "
                  + String.join(", ", new TreeSet<>(sortFields().keySet())));
        }
      }
      fields.add(field);
      directions.add(explicitDir != null ? explicitDir : field.defaultDirection());
    }

    if (fields.isEmpty()) {
      return defaultSortOrder();
    }

    return new QuerySortOrder<>(fields, directions);
  }

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

  /** Returns the set of field names supported by this entity query builder. */
  public @NotNull Set<String> availableFields() {
    return filters().keySet();
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

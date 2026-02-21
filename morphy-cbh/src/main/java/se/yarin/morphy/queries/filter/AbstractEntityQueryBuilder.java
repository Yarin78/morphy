package se.yarin.morphy.queries.filter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.Database;
import se.yarin.morphy.entities.Entity;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.entities.filters.EntityCountFilter;
import se.yarin.morphy.entities.filters.EntityFilter;
import se.yarin.morphy.entities.filters.EntityIdFilter;
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

  public @NotNull List<String> availableSortFields() {
    var fields = new ArrayList<String>();
    fields.add("id");
    fields.addAll(sortFields().keySet());
    fields.add("count");
    return fields;
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
      } else if (fieldName.equals("count")) {
        field = QuerySortField.entityCount();
      } else {
        field = sortFields().get(fieldName);
        if (field == null) {
          throw new IllegalArgumentException(
              "Unknown sort field: '"
                  + part
                  + "'. Available fields: "
                  + String.join(", ", availableSortFields()));
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

  /** Returns the field names supported by this entity query builder, in display order. */
  public @NotNull List<String> availableFields() {
    var fields = new ArrayList<String>();
    fields.add("id");
    fields.addAll(filters().keySet());
    fields.add("count");
    return fields;
  }

  private @NotNull EntityFilter<T> buildFilter(@NotNull FilterCondition condition) {
    String field = condition.field().toLowerCase();
    if (field.equals("id")) {
      return buildIdFilter(condition);
    }
    if (field.equals("count")) {
      return buildCountFilter(condition);
    }
    var builder = filters().get(field);
    if (builder == null) {
      throw new IllegalArgumentException(
          "Unknown "
              + entityLabel
              + " filter field: '"
              + condition.field()
              + "'. Available fields: "
              + String.join(", ", availableFields()));
    }
    return builder.apply(condition);
  }

  private @NotNull EntityFilter<T> buildIdFilter(@NotNull FilterCondition condition) {
    String value = condition.value();
    if (value.contains("..")) {
      String[] parts = value.split("\\.\\.", 2);
      int min = parts[0].isEmpty() ? 0 : Integer.parseInt(parts[0]);
      int max =
          parts.length > 1 && !parts[1].isEmpty()
              ? Integer.parseInt(parts[1])
              : Integer.MAX_VALUE;
      return new EntityIdFilter<>(entityType, min, max);
    }
    int id = Integer.parseInt(value);
    return new EntityIdFilter<>(entityType, id, id);
  }

  private @NotNull EntityFilter<T> buildCountFilter(@NotNull FilterCondition condition) {
    String value = condition.value();
    if (value.contains("..")) {
      String[] parts = value.split("\\.\\.", 2);
      int min = parts[0].isEmpty() ? 0 : Integer.parseInt(parts[0]);
      int max =
          parts.length > 1 && !parts[1].isEmpty()
              ? Integer.parseInt(parts[1])
              : Integer.MAX_VALUE;
      return new EntityCountFilter<>(entityType, min, max);
    }
    int count = Integer.parseInt(value);
    return new EntityCountFilter<>(entityType, count, count);
  }

  @SafeVarargs
  protected static <V> Map<String, V> orderedMap(Map.Entry<String, V>... entries) {
    var map = new LinkedHashMap<String, V>();
    for (var entry : entries) {
      map.put(entry.getKey(), entry.getValue());
    }
    return map;
  }
}

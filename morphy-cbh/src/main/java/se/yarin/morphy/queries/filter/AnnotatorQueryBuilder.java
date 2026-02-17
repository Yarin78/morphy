package se.yarin.morphy.queries.filter;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.Database;
import se.yarin.morphy.entities.Annotator;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.entities.filters.AnnotatorNameFilter;
import se.yarin.morphy.entities.filters.EntityFilter;
import se.yarin.morphy.queries.EntityQuery;

/**
 * Builds an {@link EntityQuery} for {@link Annotator} from a filter expression string.
 *
 * <p>Supported fields: name (default field: name).
 */
public class AnnotatorQueryBuilder {

  private final FilterQueryParser filterQueryParser = new FilterQueryParser("name");

  public @NotNull EntityQuery<Annotator> buildQuery(
      @NotNull Database database, @Nullable String filterExpression) {
    if (filterExpression == null || filterExpression.isBlank()) {
      return new EntityQuery<>(database, EntityType.ANNOTATOR, List.of());
    }
    List<FilterCondition> conditions = filterQueryParser.parse(filterExpression);
    return buildQuery(database, conditions);
  }

  public @NotNull EntityQuery<Annotator> buildQuery(
      @NotNull Database database, @NotNull List<FilterCondition> conditions) {
    List<EntityFilter<Annotator>> filters = new ArrayList<>();

    for (FilterCondition condition : conditions) {
      filters.add(buildFilter(condition));
    }

    return new EntityQuery<>(
        database, EntityType.ANNOTATOR, List.<EntityFilter<Annotator>>copyOf(filters));
  }

  private @NotNull EntityFilter<Annotator> buildFilter(@NotNull FilterCondition condition) {
    return switch (condition.field().toLowerCase()) {
      case "name" -> new AnnotatorNameFilter(condition.value(), false, false);
      default ->
          throw new IllegalArgumentException(
              "Unknown annotator filter field: " + condition.field());
    };
  }
}

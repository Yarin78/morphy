package se.yarin.morphy.queries.filter;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.Database;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.entities.Team;
import se.yarin.morphy.entities.filters.EntityFilter;
import se.yarin.morphy.entities.filters.TeamTitleFilter;
import se.yarin.morphy.queries.EntityQuery;

/**
 * Builds an {@link EntityQuery} for {@link Team} from a filter expression string.
 *
 * <p>Supported fields: title/name (default field: title).
 */
public class TeamQueryBuilder {

  private final FilterQueryParser filterQueryParser = new FilterQueryParser("title");

  public @NotNull EntityQuery<Team> buildQuery(
      @NotNull Database database, @Nullable String filterExpression) {
    if (filterExpression == null || filterExpression.isBlank()) {
      return new EntityQuery<>(database, EntityType.TEAM, List.of());
    }
    List<FilterCondition> conditions = filterQueryParser.parse(filterExpression);
    return buildQuery(database, conditions);
  }

  public @NotNull EntityQuery<Team> buildQuery(
      @NotNull Database database, @NotNull List<FilterCondition> conditions) {
    List<EntityFilter<Team>> filters = new ArrayList<>();

    for (FilterCondition condition : conditions) {
      filters.add(buildFilter(condition));
    }

    return new EntityQuery<>(
        database, EntityType.TEAM, List.<EntityFilter<Team>>copyOf(filters));
  }

  private @NotNull EntityFilter<Team> buildFilter(@NotNull FilterCondition condition) {
    return switch (condition.field().toLowerCase()) {
      case "title", "name" -> new TeamTitleFilter(condition.value(), false, false);
      default ->
          throw new IllegalArgumentException(
              "Unknown team filter field: " + condition.field());
    };
  }
}

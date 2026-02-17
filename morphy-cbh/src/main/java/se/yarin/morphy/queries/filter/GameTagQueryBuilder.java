package se.yarin.morphy.queries.filter;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.Database;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.entities.GameTag;
import se.yarin.morphy.entities.filters.EntityFilter;
import se.yarin.morphy.entities.filters.GameTagTitleFilter;
import se.yarin.morphy.queries.EntityQuery;

/**
 * Builds an {@link EntityQuery} for {@link GameTag} from a filter expression string.
 *
 * <p>Supported fields: name/title (default field: name).
 */
public class GameTagQueryBuilder {

  private final FilterQueryParser filterQueryParser = new FilterQueryParser("name");

  public @NotNull EntityQuery<GameTag> buildQuery(
      @NotNull Database database, @Nullable String filterExpression) {
    if (filterExpression == null || filterExpression.isBlank()) {
      return new EntityQuery<>(database, EntityType.GAME_TAG, List.of());
    }
    List<FilterCondition> conditions = filterQueryParser.parse(filterExpression);
    return buildQuery(database, conditions);
  }

  public @NotNull EntityQuery<GameTag> buildQuery(
      @NotNull Database database, @NotNull List<FilterCondition> conditions) {
    List<EntityFilter<GameTag>> filters = new ArrayList<>();

    for (FilterCondition condition : conditions) {
      filters.add(buildFilter(condition));
    }

    return new EntityQuery<>(
        database, EntityType.GAME_TAG, List.<EntityFilter<GameTag>>copyOf(filters));
  }

  private @NotNull EntityFilter<GameTag> buildFilter(@NotNull FilterCondition condition) {
    return switch (condition.field().toLowerCase()) {
      case "name", "title" -> new GameTagTitleFilter(condition.value(), false, false);
      default ->
          throw new IllegalArgumentException(
              "Unknown game tag filter field: " + condition.field());
    };
  }
}

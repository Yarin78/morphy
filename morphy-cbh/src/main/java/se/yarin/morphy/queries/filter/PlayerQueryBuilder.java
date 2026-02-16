package se.yarin.morphy.queries.filter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.Database;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.entities.Player;
import se.yarin.morphy.entities.filters.EntityFilter;
import se.yarin.morphy.entities.filters.MultiPlayerNameFilter;
import se.yarin.morphy.entities.filters.PlayerNameFilter;
import se.yarin.morphy.queries.EntityQuery;

/**
 * Builds an {@link EntityQuery} for {@link Player} from a filter expression string.
 *
 * <p>Supported fields: name (with pipe syntax for OR matching).
 */
public class PlayerQueryBuilder {

  private final FilterQueryParser filterQueryParser = new FilterQueryParser();

  /**
   * Builds a PlayerQuery from a filter expression string.
   *
   * @param database the database to query
   * @param filterExpression the filter expression (e.g., "name:Carlsen")
   * @return EntityQuery for players
   */
  public @NotNull EntityQuery<Player> buildQuery(
      @NotNull Database database, @Nullable String filterExpression) {
    if (filterExpression == null || filterExpression.isBlank()) {
      return new EntityQuery<>(database, EntityType.PLAYER, List.of());
    }
    List<FilterCondition> conditions = filterQueryParser.parse(filterExpression);
    return buildQuery(database, conditions);
  }

  /**
   * Builds a PlayerQuery from a list of FilterConditions.
   *
   * @param database the database to query
   * @param conditions the filter conditions
   * @return EntityQuery for players
   */
  public @NotNull EntityQuery<Player> buildQuery(
      @NotNull Database database, @NotNull List<FilterCondition> conditions) {
    List<EntityFilter<Player>> filters = new ArrayList<>();

    for (FilterCondition condition : conditions) {
      filters.add(buildFilter(condition));
    }

    return new EntityQuery<>(
        database, EntityType.PLAYER, List.<EntityFilter<Player>>copyOf(filters));
  }

  private @NotNull EntityFilter<Player> buildFilter(@NotNull FilterCondition condition) {
    return switch (condition.field().toLowerCase()) {
      case "name" -> {
        String value = condition.value();
        if (value.contains("|")) {
          List<String> names =
              Arrays.stream(value.split("\\|")).map(String::trim).collect(Collectors.toList());
          yield new MultiPlayerNameFilter(names, false, false);
        }
        yield new PlayerNameFilter(value, false, false);
      }
      default ->
          throw new IllegalArgumentException(
              "Unknown player filter field: " + condition.field());
    };
  }
}

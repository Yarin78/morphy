package se.yarin.morphy.queries.filter;

import java.util.Map;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.entities.Team;
import se.yarin.morphy.entities.filters.EntityFilter;
import se.yarin.morphy.entities.filters.TeamNationFilter;
import se.yarin.morphy.entities.filters.TeamNumberFilter;
import se.yarin.morphy.entities.filters.TeamTitleFilter;
import se.yarin.morphy.entities.filters.TeamYearFilter;
import se.yarin.morphy.queries.QuerySortField;
import se.yarin.morphy.queries.QuerySortOrder;

/**
 * Builds an {@link se.yarin.morphy.queries.EntityQuery} for {@link Team} from a filter expression
 * string.
 *
 * <p>Supported fields: title/name, number, year, nation (default field: title).
 */
public class TeamQueryBuilder extends AbstractEntityQueryBuilder<Team> {

  private static final Map<String, Function<FilterCondition, EntityFilter<Team>>> FILTERS =
      orderedMap(
          Map.entry("title", c -> new TeamTitleFilter(c.value(), false, false)),
          Map.entry("name", c -> new TeamTitleFilter(c.value(), false, false)),
          Map.entry("number", TeamQueryBuilder::buildNumberFilter),
          Map.entry("year", TeamQueryBuilder::buildYearFilter),
          Map.entry("nation", c -> new TeamNationFilter(c.value())));

  public TeamQueryBuilder() {
    super(EntityType.TEAM, "team", "title");
  }

  private static final Map<String, QuerySortField<Team>> SORT_FIELDS =
      orderedMap(
          Map.entry("title", QuerySortField.teamTitle()),
          Map.entry("number", QuerySortField.teamNumber()),
          Map.entry("season", QuerySortField.teamSeason()),
          Map.entry("year", QuerySortField.teamYear()),
          Map.entry("nation", QuerySortField.teamNation()));

  @Override
  protected Map<String, Function<FilterCondition, EntityFilter<Team>>> filters() {
    return FILTERS;
  }

  @Override
  protected Map<String, QuerySortField<Team>> sortFields() {
    return SORT_FIELDS;
  }

  @Override
  protected QuerySortOrder<Team> defaultSortOrder() {
    return QuerySortOrder.byTeamDefaultIndex();
  }

  private static @NotNull EntityFilter<Team> buildNumberFilter(
      @NotNull FilterCondition condition) {
    IntRange range = IntRange.parse(condition, Integer.MAX_VALUE);
    return new TeamNumberFilter(range.min(), range.max());
  }

  private static @NotNull EntityFilter<Team> buildYearFilter(@NotNull FilterCondition condition) {
    IntRange range = IntRange.parse(condition, 9999);
    return new TeamYearFilter(range.min(), range.max());
  }
}

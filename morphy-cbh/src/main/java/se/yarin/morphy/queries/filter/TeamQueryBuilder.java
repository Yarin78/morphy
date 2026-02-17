package se.yarin.morphy.queries.filter;

import java.util.Map;
import java.util.function.Function;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.entities.Team;
import se.yarin.morphy.entities.filters.EntityFilter;
import se.yarin.morphy.entities.filters.TeamTitleFilter;

/**
 * Builds an {@link se.yarin.morphy.queries.EntityQuery} for {@link Team} from a filter expression
 * string.
 *
 * <p>Supported fields: title/name (default field: title).
 */
public class TeamQueryBuilder extends AbstractEntityQueryBuilder<Team> {

  private static final Map<String, Function<FilterCondition, EntityFilter<Team>>> FILTERS =
      Map.ofEntries(
          Map.entry("title", c -> new TeamTitleFilter(c.value(), false, false)),
          Map.entry("name", c -> new TeamTitleFilter(c.value(), false, false)));

  public TeamQueryBuilder() {
    super(EntityType.TEAM, "team", "title");
  }

  @Override
  protected Map<String, Function<FilterCondition, EntityFilter<Team>>> filters() {
    return FILTERS;
  }
}

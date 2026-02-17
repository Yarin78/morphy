package se.yarin.morphy.queries.filter;

import java.util.Map;
import java.util.function.Function;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.entities.Source;
import se.yarin.morphy.entities.filters.EntityFilter;
import se.yarin.morphy.entities.filters.SourceTitleFilter;

/**
 * Builds an {@link se.yarin.morphy.queries.EntityQuery} for {@link Source} from a filter expression
 * string.
 *
 * <p>Supported fields: title/name (default field: title).
 */
public class SourceQueryBuilder extends AbstractEntityQueryBuilder<Source> {

  private static final Map<String, Function<FilterCondition, EntityFilter<Source>>> FILTERS =
      Map.ofEntries(
          Map.entry("title", c -> new SourceTitleFilter(c.value(), false, false)),
          Map.entry("name", c -> new SourceTitleFilter(c.value(), false, false)));

  public SourceQueryBuilder() {
    super(EntityType.SOURCE, "source", "title");
  }

  @Override
  protected Map<String, Function<FilterCondition, EntityFilter<Source>>> filters() {
    return FILTERS;
  }
}

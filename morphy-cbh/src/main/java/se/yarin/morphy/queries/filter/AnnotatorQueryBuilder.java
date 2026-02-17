package se.yarin.morphy.queries.filter;

import java.util.Map;
import java.util.function.Function;
import se.yarin.morphy.entities.Annotator;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.entities.filters.AnnotatorNameFilter;
import se.yarin.morphy.entities.filters.EntityFilter;

/**
 * Builds an {@link se.yarin.morphy.queries.EntityQuery} for {@link Annotator} from a filter
 * expression string.
 *
 * <p>Supported fields: name (default field: name).
 */
public class AnnotatorQueryBuilder extends AbstractEntityQueryBuilder<Annotator> {

  private static final Map<String, Function<FilterCondition, EntityFilter<Annotator>>> FILTERS =
      Map.ofEntries(
          Map.entry("name", c -> new AnnotatorNameFilter(c.value(), false, false)));

  public AnnotatorQueryBuilder() {
    super(EntityType.ANNOTATOR, "annotator", "name");
  }

  @Override
  protected Map<String, Function<FilterCondition, EntityFilter<Annotator>>> filters() {
    return FILTERS;
  }
}

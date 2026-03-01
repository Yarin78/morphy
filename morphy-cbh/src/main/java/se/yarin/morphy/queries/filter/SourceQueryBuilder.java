package se.yarin.morphy.queries.filter;

import java.util.Map;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.entities.Source;
import se.yarin.morphy.entities.filters.EntityFilter;
import se.yarin.morphy.entities.filters.SourceDateFilter;
import se.yarin.morphy.entities.filters.SourcePublicationFilter;
import se.yarin.morphy.entities.filters.SourcePublisherFilter;
import se.yarin.morphy.entities.filters.SourceQualityFilter;
import se.yarin.morphy.entities.filters.SourceTitleFilter;
import se.yarin.morphy.entities.filters.SourceVersionFilter;
import se.yarin.morphy.queries.QuerySortField;
import se.yarin.morphy.queries.QuerySortOrder;

/**
 * Builds an {@link se.yarin.morphy.queries.EntityQuery} for {@link Source} from a filter expression
 * string.
 *
 * <p>Supported fields: title, publisher, date, publication, version, quality (default field:
 * title).
 */
public class SourceQueryBuilder extends AbstractEntityQueryBuilder<Source> {

  private static final Map<String, Function<FilterCondition, EntityFilter<Source>>> FILTERS =
      orderedMap(
          Map.entry("title", c -> new SourceTitleFilter(c.value(), false, false)),
          Map.entry("publisher", c -> new SourcePublisherFilter(c.value(), false, false)),
          Map.entry("date", SourceQueryBuilder::buildDateFilter),
          Map.entry("publication", SourceQueryBuilder::buildPublicationFilter),
          Map.entry("version", SourceQueryBuilder::buildVersionFilter),
          Map.entry("quality", c -> new SourceQualityFilter(c.value())));

  public SourceQueryBuilder() {
    super(EntityType.SOURCE, "source", "title");
  }

  private static final Map<String, QuerySortField<Source>> SORT_FIELDS =
      orderedMap(
          Map.entry("title", QuerySortField.sourceTitle()),
          Map.entry("publisher", QuerySortField.sourcePublisher()),
          Map.entry("date", QuerySortField.sourceDate()),
          Map.entry("publication", QuerySortField.sourcePublication()),
          Map.entry("version", QuerySortField.sourceVersion()),
          Map.entry("quality", QuerySortField.sourceQuality()));

  @Override
  protected Map<String, Function<FilterCondition, EntityFilter<Source>>> filters() {
    return FILTERS;
  }

  @Override
  protected Map<String, QuerySortField<Source>> sortFields() {
    return SORT_FIELDS;
  }

  @Override
  protected QuerySortOrder<Source> defaultSortOrder() {
    return QuerySortOrder.bySourceDefaultIndex();
  }

  private static @NotNull EntityFilter<Source> buildDateFilter(
      @NotNull FilterCondition condition) {
    PartialDateParser.DateRange range = PartialDateParser.parseRange(condition);
    return new SourceDateFilter(range.from(), range.to());
  }

  private static @NotNull EntityFilter<Source> buildPublicationFilter(
      @NotNull FilterCondition condition) {
    PartialDateParser.DateRange range = PartialDateParser.parseRange(condition);
    return new SourcePublicationFilter(range.from(), range.to());
  }

  private static @NotNull EntityFilter<Source> buildVersionFilter(
      @NotNull FilterCondition condition) {
    IntRange range = IntRange.parse(condition, Integer.MAX_VALUE);
    return new SourceVersionFilter(range.min(), range.max());
  }
}

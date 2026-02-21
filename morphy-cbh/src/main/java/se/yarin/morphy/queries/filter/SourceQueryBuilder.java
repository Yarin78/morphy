package se.yarin.morphy.queries.filter;

import java.util.Map;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import se.yarin.chess.Date;
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
      Map.ofEntries(
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
      Map.of(
          "title", QuerySortField.sourceTitle(),
          "publisher", QuerySortField.sourcePublisher(),
          "date", QuerySortField.sourceDate(),
          "publication", QuerySortField.sourcePublication(),
          "version", QuerySortField.sourceVersion(),
          "quality", QuerySortField.sourceQuality(),
          "count", QuerySortField.entityCount());

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
    String value = condition.value();
    if (value.contains("..")) {
      String[] parts = value.split("\\.\\.", 2);
      Date from = parts[0].isEmpty() ? Date.unset() : PartialDateParser.parse(parts[0]).from();
      Date to =
          parts.length > 1 && !parts[1].isEmpty()
              ? PartialDateParser.parse(parts[1]).to()
              : Date.unset();
      return new SourceDateFilter(from, to);
    }
    PartialDateParser.DateRange range = PartialDateParser.parse(value);
    return new SourceDateFilter(range.from(), range.to());
  }

  private static @NotNull EntityFilter<Source> buildPublicationFilter(
      @NotNull FilterCondition condition) {
    String value = condition.value();
    if (value.contains("..")) {
      String[] parts = value.split("\\.\\.", 2);
      Date from = parts[0].isEmpty() ? Date.unset() : PartialDateParser.parse(parts[0]).from();
      Date to =
          parts.length > 1 && !parts[1].isEmpty()
              ? PartialDateParser.parse(parts[1]).to()
              : Date.unset();
      return new SourcePublicationFilter(from, to);
    }
    PartialDateParser.DateRange range = PartialDateParser.parse(value);
    return new SourcePublicationFilter(range.from(), range.to());
  }

  private static @NotNull EntityFilter<Source> buildVersionFilter(
      @NotNull FilterCondition condition) {
    String value = condition.value();
    if (value.contains("..")) {
      String[] parts = value.split("\\.\\.", 2);
      int min = parts[0].isEmpty() ? 0 : Integer.parseInt(parts[0]);
      int max =
          parts.length > 1 && !parts[1].isEmpty()
              ? Integer.parseInt(parts[1])
              : Integer.MAX_VALUE;
      return new SourceVersionFilter(min, max);
    }
    int version = Integer.parseInt(value);
    return new SourceVersionFilter(version, version);
  }
}

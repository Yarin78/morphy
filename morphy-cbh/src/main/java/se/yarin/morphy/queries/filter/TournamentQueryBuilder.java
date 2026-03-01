package se.yarin.morphy.queries.filter;

import java.util.Map;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.entities.Tournament;
import se.yarin.morphy.entities.filters.*;
import se.yarin.morphy.queries.QuerySortField;
import se.yarin.morphy.queries.QuerySortOrder;

/**
 * Builds an {@link se.yarin.morphy.queries.EntityQuery} for {@link Tournament} from a filter
 * expression string.
 *
 * <p>Supported fields: title, date, type, time, place, nation, category, rounds, teams, year.
 */
public class TournamentQueryBuilder extends AbstractEntityQueryBuilder<Tournament> {

  private static final Map<String, Function<FilterCondition, EntityFilter<Tournament>>> FILTERS =
      orderedMap(
          Map.entry("title", c -> new TournamentTitleFilter(c.value(), false, false)),
          Map.entry("date", TournamentQueryBuilder::buildDateFilter),
          Map.entry("type", c -> new TournamentTypeFilter(c.value())),
          Map.entry("time", c -> new TournamentTimeControlFilter(c.value())),
          Map.entry("place", c -> new TournamentPlaceFilter(c.value(), false, false)),
          Map.entry("nation", c -> new TournamentNationFilter(c.value())),
          Map.entry("category", TournamentQueryBuilder::buildCategoryFilter),
          Map.entry("rounds", TournamentQueryBuilder::buildRoundsFilter),
          Map.entry("team", c -> new TournamentTeamFilter(Boolean.parseBoolean(c.value()))));

  private static final Map<String, QuerySortField<Tournament>> SORT_FIELDS =
      orderedMap(
          Map.entry("title", QuerySortField.tournamentTitle()),
          Map.entry("year", QuerySortField.tournamentYear()),
          Map.entry("startdate", QuerySortField.tournamentStartDate()),
          Map.entry("date", QuerySortField.tournamentStartDate()),
          Map.entry("place", QuerySortField.tournamentPlace()),
          Map.entry("combinedtype", QuerySortField.tournamentCombinedType()),
          Map.entry("nation", QuerySortField.tournamentNation()),
          Map.entry("category", QuerySortField.tournamentCategory()),
          Map.entry("rounds", QuerySortField.tournamentRounds()),
          Map.entry("complete", QuerySortField.tournamentComplete()),
          Map.entry("enddate", QuerySortField.tournamentEndDate()),
          Map.entry("coordinates", QuerySortField.tournamentCoordinates()),
          Map.entry("tiebreak", QuerySortField.tournamentTiebreak()));

  public TournamentQueryBuilder() {
    super(EntityType.TOURNAMENT, "tournament", "title");
  }

  @Override
  protected Map<String, Function<FilterCondition, EntityFilter<Tournament>>> filters() {
    return FILTERS;
  }

  @Override
  protected Map<String, QuerySortField<Tournament>> sortFields() {
    return SORT_FIELDS;
  }

  @Override
  protected QuerySortOrder<Tournament> defaultSortOrder() {
    return QuerySortOrder.byTournamentDefaultIndex();
  }

  private static @NotNull EntityFilter<Tournament> buildDateFilter(
      @NotNull FilterCondition condition) {
    PartialDateParser.DateRange range = PartialDateParser.parseRange(condition);
    return new TournamentStartDateFilter(range.from(), range.to());
  }

  private static @NotNull EntityFilter<Tournament> buildCategoryFilter(
      @NotNull FilterCondition condition) {
    IntRange range = IntRange.parse(condition, 100);
    // Single value means "at least this category"
    return new TournamentCategoryFilter(
        range.min(), range.min() == range.max() ? 100 : range.max());
  }

  private static @NotNull EntityFilter<Tournament> buildRoundsFilter(
      @NotNull FilterCondition condition) {
    IntRange range = IntRange.parse(condition, 999);
    return new TournamentRoundsFilter(range.min(), range.max());
  }

}

package se.yarin.morphy.queries.filter;

import java.util.Map;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import se.yarin.chess.Date;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.entities.Tournament;
import se.yarin.morphy.entities.filters.*;
import se.yarin.morphy.queries.QuerySortField;
import se.yarin.morphy.queries.QuerySortOrder;

/**
 * Builds an {@link se.yarin.morphy.queries.EntityQuery} for {@link Tournament} from a filter
 * expression string.
 *
 * <p>Supported fields: name/title, date, type, time, place, nation, category, rounds, teams, year.
 */
public class TournamentQueryBuilder extends AbstractEntityQueryBuilder<Tournament> {

  private static final Map<String, Function<FilterCondition, EntityFilter<Tournament>>> FILTERS =
      Map.ofEntries(
          Map.entry("name", c -> new TournamentTitleFilter(c.value(), false, false)),
          Map.entry("title", c -> new TournamentTitleFilter(c.value(), false, false)),
          Map.entry("date", TournamentQueryBuilder::buildDateFilter),
          Map.entry("type", c -> new TournamentTypeFilter(c.value())),
          Map.entry("time", c -> new TournamentTimeControlFilter(c.value())),
          Map.entry("place", c -> new TournamentPlaceFilter(c.value(), false, false)),
          Map.entry("nation", c -> new TournamentNationFilter(c.value())),
          Map.entry("category", TournamentQueryBuilder::buildCategoryFilter),
          Map.entry("rounds", TournamentQueryBuilder::buildRoundsFilter),
          Map.entry("teams", c -> new TournamentTeamFilter()),
          Map.entry(
              "year",
              c -> {
                int year = Integer.parseInt(c.value());
                return new TournamentStartDateFilter(
                    new Date(year, 1, 1), new Date(year, 12, 31));
              }));

  private static final Map<String, QuerySortField<Tournament>> SORT_FIELDS =
      Map.of(
          "title", QuerySortField.tournamentTitle(),
          "year", QuerySortField.tournamentYear(),
          "startdate", QuerySortField.tournamentStartDate(),
          "date", QuerySortField.tournamentStartDate(),
          "place", QuerySortField.tournamentPlace(),
          "count", QuerySortField.entityCount());

  public TournamentQueryBuilder() {
    super(EntityType.TOURNAMENT, "tournament", "name");
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
    if ("..".equals(condition.operator())) {
      String[] parts = condition.value().split("\\.\\.", 2);
      Date from = parts[0].isEmpty() ? Date.unset() : parseDateStart(parts[0]);
      Date to = parts.length > 1 && !parts[1].isEmpty() ? parseDateEnd(parts[1]) : Date.unset();
      return new TournamentStartDateFilter(from, to);
    } else {
      String value = condition.value();
      if (value.contains("..")) {
        String[] parts = value.split("\\.\\.", 2);
        Date from = parts[0].isEmpty() ? Date.unset() : parseDateStart(parts[0]);
        Date to =
            parts.length > 1 && !parts[1].isEmpty() ? parseDateEnd(parts[1]) : Date.unset();
        return new TournamentStartDateFilter(from, to);
      }
      PartialDateParser.DateRange range = PartialDateParser.parse(value);
      return new TournamentStartDateFilter(range.from(), range.to());
    }
  }

  private static @NotNull EntityFilter<Tournament> buildCategoryFilter(
      @NotNull FilterCondition condition) {
    if ("..".equals(condition.operator())) {
      String[] parts = condition.value().split("\\.\\.", 2);
      int min = parts[0].isEmpty() ? 0 : Integer.parseInt(parts[0]);
      int max = parts.length > 1 && !parts[1].isEmpty() ? Integer.parseInt(parts[1]) : 100;
      return new TournamentCategoryFilter(min, max);
    } else {
      String value = condition.value();
      if (value.contains("..")) {
        String[] parts = value.split("\\.\\.", 2);
        int min = parts[0].isEmpty() ? 0 : Integer.parseInt(parts[0]);
        int max = parts.length > 1 && !parts[1].isEmpty() ? Integer.parseInt(parts[1]) : 100;
        return new TournamentCategoryFilter(min, max);
      }
      int cat = Integer.parseInt(value);
      return new TournamentCategoryFilter(cat, 100);
    }
  }

  private static @NotNull EntityFilter<Tournament> buildRoundsFilter(
      @NotNull FilterCondition condition) {
    if ("..".equals(condition.operator())) {
      String[] parts = condition.value().split("\\.\\.", 2);
      int min = parts[0].isEmpty() ? 0 : Integer.parseInt(parts[0]);
      int max = parts.length > 1 && !parts[1].isEmpty() ? Integer.parseInt(parts[1]) : 999;
      return new TournamentRoundsFilter(min, max);
    } else {
      String value = condition.value();
      if (value.contains("..")) {
        String[] parts = value.split("\\.\\.", 2);
        int min = parts[0].isEmpty() ? 0 : Integer.parseInt(parts[0]);
        int max = parts.length > 1 && !parts[1].isEmpty() ? Integer.parseInt(parts[1]) : 999;
        return new TournamentRoundsFilter(min, max);
      }
      int rounds = Integer.parseInt(value);
      return new TournamentRoundsFilter(rounds, rounds);
    }
  }

  private static @NotNull Date parseDateStart(@NotNull String dateStr) {
    return PartialDateParser.parse(dateStr).from();
  }

  private static @NotNull Date parseDateEnd(@NotNull String dateStr) {
    return PartialDateParser.parse(dateStr).to();
  }
}

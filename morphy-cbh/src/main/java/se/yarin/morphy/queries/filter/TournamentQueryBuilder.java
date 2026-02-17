package se.yarin.morphy.queries.filter;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.chess.Date;
import se.yarin.morphy.Database;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.entities.Tournament;
import se.yarin.morphy.entities.filters.*;
import se.yarin.morphy.queries.EntityQuery;

/**
 * Builds an {@link EntityQuery} for {@link Tournament} from a filter expression string.
 *
 * <p>Supported fields: name/title, date, type, time, place, nation, category, rounds, teams.
 */
public class TournamentQueryBuilder {

  private final FilterQueryParser filterQueryParser = new FilterQueryParser("name");

  /**
   * Builds a TournamentQuery from a filter expression string.
   *
   * @param database the database to query
   * @param filterExpression the filter expression (e.g., "name:Candidates AND type:swiss")
   * @return EntityQuery for tournaments
   */
  public @NotNull EntityQuery<Tournament> buildQuery(
      @NotNull Database database, @Nullable String filterExpression) {
    if (filterExpression == null || filterExpression.isBlank()) {
      return new EntityQuery<>(database, EntityType.TOURNAMENT, List.of());
    }
    List<FilterCondition> conditions = filterQueryParser.parse(filterExpression);
    return buildQuery(database, conditions);
  }

  /**
   * Builds a TournamentQuery from a list of FilterConditions.
   *
   * @param database the database to query
   * @param conditions the filter conditions
   * @return EntityQuery for tournaments
   */
  public @NotNull EntityQuery<Tournament> buildQuery(
      @NotNull Database database, @NotNull List<FilterCondition> conditions) {
    List<EntityFilter<Tournament>> filters = new ArrayList<>();

    for (FilterCondition condition : conditions) {
      filters.add(buildFilter(condition));
    }

    return new EntityQuery<>(
        database, EntityType.TOURNAMENT, List.<EntityFilter<Tournament>>copyOf(filters));
  }

  private @NotNull EntityFilter<Tournament> buildFilter(@NotNull FilterCondition condition) {
    return switch (condition.field().toLowerCase()) {
      case "name", "title" -> new TournamentTitleFilter(condition.value(), false, false);
      case "date" -> buildDateFilter(condition);
      case "type" -> new TournamentTypeFilter(condition.value());
      case "time" -> new TournamentTimeControlFilter(condition.value());
      case "place" -> new TournamentPlaceFilter(condition.value(), false, false);
      case "nation" -> new TournamentNationFilter(condition.value());
      case "category" -> buildCategoryFilter(condition);
      case "rounds" -> buildRoundsFilter(condition);
      case "teams" -> new TournamentTeamFilter();
      case "year" -> {
        int year = Integer.parseInt(condition.value());
        yield new TournamentStartDateFilter(new Date(year, 1, 1), new Date(year, 12, 31));
      }
      default ->
          throw new IllegalArgumentException("Unknown tournament filter field: " + condition.field());
    };
  }

  private @NotNull EntityFilter<Tournament> buildDateFilter(@NotNull FilterCondition condition) {
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
        Date to = parts.length > 1 && !parts[1].isEmpty() ? parseDateEnd(parts[1]) : Date.unset();
        return new TournamentStartDateFilter(from, to);
      }
      PartialDateParser.DateRange range = PartialDateParser.parse(value);
      return new TournamentStartDateFilter(range.from(), range.to());
    }
  }

  private @NotNull EntityFilter<Tournament> buildCategoryFilter(
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

  private @NotNull EntityFilter<Tournament> buildRoundsFilter(@NotNull FilterCondition condition) {
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

  private @NotNull Date parseDateStart(@NotNull String dateStr) {
    return PartialDateParser.parse(dateStr).from();
  }

  private @NotNull Date parseDateEnd(@NotNull String dateStr) {
    return PartialDateParser.parse(dateStr).to();
  }
}

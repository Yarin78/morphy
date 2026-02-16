package se.yarin.morphy.queries.filter;

import java.util.*;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.chess.Date;
import se.yarin.morphy.Database;
import se.yarin.morphy.entities.*;
import se.yarin.morphy.entities.filters.*;
import se.yarin.morphy.games.filters.*;
import se.yarin.morphy.queries.*;

/**
 * Builds a {@link GameQuery} from a filter expression string or a list of {@link FilterCondition}s.
 *
 * <p>Supports game property filters (result, date, rating, eco, round, type), entity ID filters
 * (playerId, tournamentId, etc.), and entity property filters (player.name, tournament.title,
 * etc.).
 *
 * <p>Multiple conditions on the same entity type are combined into a single {@link GameEntityJoin}
 * with all filters, avoiding the bug where separate joins could match different entities.
 */
public class GameQueryBuilder {
  private static final Logger log = LoggerFactory.getLogger(GameQueryBuilder.class);

  private final FilterQueryParser filterQueryParser = new FilterQueryParser();

  /**
   * Builds a GameQuery from a filter expression string.
   *
   * @param database the database to query
   * @param filterExpression the filter expression (e.g., "result:1-0 AND player.name:Carlsen")
   * @return GameQuery ready for execution via QueryPlanner
   */
  public @NotNull GameQuery buildQuery(
      @NotNull Database database, @Nullable String filterExpression) {
    if (filterExpression == null || filterExpression.isBlank()) {
      return new GameQuery(database, List.of(), List.of());
    }
    List<FilterCondition> conditions = filterQueryParser.parse(filterExpression);
    return buildQuery(database, conditions);
  }

  /**
   * Builds a GameQuery from a list of FilterConditions.
   *
   * @param database the database to query
   * @param conditions the filter conditions
   * @return GameQuery ready for execution via QueryPlanner
   */
  public @NotNull GameQuery buildQuery(
      @NotNull Database database, @NotNull List<FilterCondition> conditions) {
    List<GameFilter> gameFilters = new ArrayList<>();

    // Group entity property conditions by entity type to combine them into single joins
    Map<String, List<FilterCondition>> entityConditions = new LinkedHashMap<>();

    for (FilterCondition condition : conditions) {
      if (isGamePropertyFilter(condition)) {
        gameFilters.add(buildGameFilter(condition));
      } else if (isEntityIdFilter(condition)) {
        gameFilters.add(buildEntityIdFilter(condition));
      } else if (isEntityPropertyFilter(condition)) {
        String entityType = condition.field().split("\\.", 2)[0].toLowerCase();
        entityConditions.computeIfAbsent(entityType, k -> new ArrayList<>()).add(condition);
      } else {
        throw new IllegalArgumentException("Unknown filter field: " + condition.field());
      }
    }

    // Build combined entity joins
    List<GameEntityJoin<?>> entityJoins = new ArrayList<>();
    for (var entry : entityConditions.entrySet()) {
      entityJoins.add(buildCombinedEntityJoin(database, entry.getKey(), entry.getValue()));
    }

    return new GameQuery(database, gameFilters, entityJoins);
  }

  private boolean isGamePropertyFilter(@NotNull FilterCondition condition) {
    String field = condition.field().toLowerCase();
    return field.equals("result")
        || field.equals("date")
        || field.equals("rating")
        || field.equals("eco")
        || field.equals("round")
        || field.equals("type");
  }

  private boolean isEntityIdFilter(@NotNull FilterCondition condition) {
    if (condition.field().contains(".")) return false;
    String field = condition.field().toLowerCase();
    return field.equals("playerid")
        || field.equals("player")
        || field.equals("tournamentid")
        || field.equals("tournament")
        || field.equals("annotatorid")
        || field.equals("annotator")
        || field.equals("sourceid")
        || field.equals("source")
        || field.equals("teamid")
        || field.equals("team")
        || field.equals("gametagid")
        || field.equals("gametag");
  }

  private boolean isEntityPropertyFilter(@NotNull FilterCondition condition) {
    return condition.field().contains(".");
  }

  private @NotNull GameFilter buildGameFilter(@NotNull FilterCondition condition) {
    return switch (condition.field().toLowerCase()) {
      case "result" -> new ResultsFilter(condition.value());
      case "date" -> buildDateFilter(condition);
      case "rating" -> buildRatingFilter(condition);
      case "eco" -> new EcoFilter(condition.value());
      case "round" -> buildRoundFilter(condition);
      case "type" -> buildTypeFilter(condition);
      default ->
          throw new IllegalArgumentException("Unknown game filter: " + condition.field());
    };
  }

  private @NotNull GameFilter buildEntityIdFilter(@NotNull FilterCondition condition) {
    int entityId = Integer.parseInt(condition.value());
    String field = condition.field().toLowerCase();

    return switch (field) {
      case "playerid", "player" -> {
        String position = condition.modifiers().getOrDefault("position", "any");
        yield new PlayerFilter(entityId, parsePlayerPosition(position));
      }
      case "tournamentid", "tournament" -> new TournamentFilter(entityId);
      case "annotatorid", "annotator" -> new AnnotatorFilter(entityId);
      case "sourceid", "source" -> new SourceFilter(entityId);
      case "teamid", "team" -> {
        String position = condition.modifiers().getOrDefault("position", "any");
        yield new TeamFilter(entityId, parseTeamPosition(position));
      }
      case "gametagid", "gametag" -> new GameTagFilter(entityId);
      default ->
          throw new IllegalArgumentException("Unknown entity ID filter: " + condition.field());
    };
  }

  /**
   * Builds a combined GameEntityJoin for all conditions targeting the same entity type. This fixes
   * the bug where separate joins on the same entity type could match different entities.
   */
  private @NotNull GameEntityJoin<?> buildCombinedEntityJoin(
      @NotNull Database database,
      @NotNull String entityType,
      @NotNull List<FilterCondition> conditions) {
    return switch (entityType) {
      case "player" -> buildPlayerJoin(database, conditions);
      case "tournament" -> buildTournamentJoin(database, conditions);
      case "annotator" -> buildAnnotatorJoin(database, conditions);
      case "source" -> buildSourceJoin(database, conditions);
      case "team" -> buildTeamJoin(database, conditions);
      case "gametag" -> buildGameTagJoin(database, conditions);
      default -> throw new IllegalArgumentException("Unknown entity type: " + entityType);
    };
  }

  private @NotNull GameEntityJoin<Player> buildPlayerJoin(
      @NotNull Database database, @NotNull List<FilterCondition> conditions) {
    List<EntityFilter<Player>> filters = new ArrayList<>();
    GameEntityJoinCondition joinCondition = GameEntityJoinCondition.ANY;

    for (FilterCondition condition : conditions) {
      String property = condition.field().split("\\.", 2)[1].toLowerCase();
      if (!"name".equals(property)) {
        throw new IllegalArgumentException("Unknown player property: " + property);
      }

      String value = condition.value();
      if (value.contains("|")) {
        // Pipe syntax: player.name:Carlsen|Caruana -> MultiPlayerNameFilter
        List<String> names =
            Arrays.stream(value.split("\\|")).map(String::trim).collect(Collectors.toList());
        filters.add(new MultiPlayerNameFilter(names, false, false));
      } else {
        filters.add(new PlayerNameFilter(value, false, false));
      }

      String position = condition.modifiers().getOrDefault("position", null);
      if (position != null) {
        joinCondition = parsePlayerPosition(position);
      }
    }

    EntityQuery<Player> entityQuery =
        new EntityQuery<>(database, EntityType.PLAYER, List.copyOf(filters));
    return new GameEntityJoin<>(entityQuery, joinCondition);
  }

  private @NotNull GameEntityJoin<Tournament> buildTournamentJoin(
      @NotNull Database database, @NotNull List<FilterCondition> conditions) {
    List<EntityFilter<Tournament>> filters = new ArrayList<>();

    for (FilterCondition condition : conditions) {
      String property = condition.field().split("\\.", 2)[1].toLowerCase();
      filters.add(buildTournamentFilter(condition, property));
    }

    EntityQuery<Tournament> entityQuery =
        new EntityQuery<>(database, EntityType.TOURNAMENT, List.copyOf(filters));
    return new GameEntityJoin<>(entityQuery, null);
  }

  private @NotNull EntityFilter<Tournament> buildTournamentFilter(
      @NotNull FilterCondition condition, @NotNull String property) {
    return switch (property) {
      case "title", "name" -> new TournamentTitleFilter(condition.value(), false, false);
      case "place" -> new TournamentPlaceFilter(condition.value(), false, false);
      case "type" -> new TournamentTypeFilter(condition.value());
      case "time" -> new TournamentTimeControlFilter(condition.value());
      case "nation" -> new TournamentNationFilter(condition.value());
      case "date" -> buildTournamentDateFilter(condition);
      case "year" -> {
        // "year" is an alias: tournament.year:2024 -> date range for that year
        int year = Integer.parseInt(condition.value());
        yield new TournamentStartDateFilter(new Date(year, 1, 1), new Date(year, 12, 31));
      }
      case "category" -> buildTournamentCategoryFilter(condition);
      case "rounds" -> buildTournamentRoundsFilter(condition);
      case "teams" -> new TournamentTeamFilter();
      default ->
          throw new IllegalArgumentException("Unknown tournament property: " + property);
    };
  }

  private @NotNull EntityFilter<Tournament> buildTournamentDateFilter(
      @NotNull FilterCondition condition) {
    if ("..".equals(condition.operator())) {
      String[] parts = condition.value().split("\\.\\.", 2);
      Date from = parts[0].isEmpty() ? Date.unset() : parseDateStart(parts[0]);
      Date to = parts.length > 1 && !parts[1].isEmpty() ? parseDateEnd(parts[1]) : Date.unset();
      return new TournamentStartDateFilter(from, to);
    } else {
      PartialDateParser.DateRange range = PartialDateParser.parse(condition.value());
      return new TournamentStartDateFilter(range.from(), range.to());
    }
  }

  private @NotNull EntityFilter<Tournament> buildTournamentCategoryFilter(
      @NotNull FilterCondition condition) {
    if ("..".equals(condition.operator())) {
      String[] parts = condition.value().split("\\.\\.", 2);
      int min = parts[0].isEmpty() ? 0 : Integer.parseInt(parts[0]);
      int max = parts.length > 1 && !parts[1].isEmpty() ? Integer.parseInt(parts[1]) : 100;
      return new TournamentCategoryFilter(min, max);
    } else {
      String value = condition.value();
      if (value.endsWith("..")) {
        // "20.." syntax via colon operator
        int min = Integer.parseInt(value.substring(0, value.length() - 2));
        return new TournamentCategoryFilter(min, 100);
      }
      int cat = Integer.parseInt(value);
      return new TournamentCategoryFilter(cat, cat);
    }
  }

  private @NotNull EntityFilter<Tournament> buildTournamentRoundsFilter(
      @NotNull FilterCondition condition) {
    if ("..".equals(condition.operator())) {
      String[] parts = condition.value().split("\\.\\.", 2);
      int min = parts[0].isEmpty() ? 0 : Integer.parseInt(parts[0]);
      int max = parts.length > 1 && !parts[1].isEmpty() ? Integer.parseInt(parts[1]) : 999;
      return new TournamentRoundsFilter(min, max);
    } else {
      String value = condition.value();
      if (value.contains("..")) {
        // "5..13" via colon operator
        String[] parts = value.split("\\.\\.", 2);
        int min = parts[0].isEmpty() ? 0 : Integer.parseInt(parts[0]);
        int max = parts.length > 1 && !parts[1].isEmpty() ? Integer.parseInt(parts[1]) : 999;
        return new TournamentRoundsFilter(min, max);
      }
      int rounds = Integer.parseInt(value);
      return new TournamentRoundsFilter(rounds, rounds);
    }
  }

  private @NotNull GameEntityJoin<Annotator> buildAnnotatorJoin(
      @NotNull Database database, @NotNull List<FilterCondition> conditions) {
    List<EntityFilter<Annotator>> filters = new ArrayList<>();

    for (FilterCondition condition : conditions) {
      String property = condition.field().split("\\.", 2)[1].toLowerCase();
      if (!"name".equals(property)) {
        throw new IllegalArgumentException("Unknown annotator property: " + property);
      }
      filters.add(new AnnotatorNameFilter(condition.value(), false, false));
    }

    EntityQuery<Annotator> entityQuery =
        new EntityQuery<>(database, EntityType.ANNOTATOR, List.copyOf(filters));
    return new GameEntityJoin<>(entityQuery, null);
  }

  private @NotNull GameEntityJoin<Source> buildSourceJoin(
      @NotNull Database database, @NotNull List<FilterCondition> conditions) {
    List<EntityFilter<Source>> filters = new ArrayList<>();

    for (FilterCondition condition : conditions) {
      String property = condition.field().split("\\.", 2)[1].toLowerCase();
      if (!"title".equals(property) && !"name".equals(property)) {
        throw new IllegalArgumentException("Unknown source property: " + property);
      }
      filters.add(new SourceTitleFilter(condition.value(), false, false));
    }

    EntityQuery<Source> entityQuery =
        new EntityQuery<>(database, EntityType.SOURCE, List.copyOf(filters));
    return new GameEntityJoin<>(entityQuery, null);
  }

  private @NotNull GameEntityJoin<Team> buildTeamJoin(
      @NotNull Database database, @NotNull List<FilterCondition> conditions) {
    List<EntityFilter<Team>> filters = new ArrayList<>();
    GameEntityJoinCondition joinCondition = GameEntityJoinCondition.ANY;

    for (FilterCondition condition : conditions) {
      String property = condition.field().split("\\.", 2)[1].toLowerCase();
      if (!"title".equals(property) && !"name".equals(property)) {
        throw new IllegalArgumentException("Unknown team property: " + property);
      }
      filters.add(new TeamTitleFilter(condition.value(), false, false));

      String position = condition.modifiers().getOrDefault("position", null);
      if (position != null) {
        joinCondition = parseTeamPosition(position);
      }
    }

    EntityQuery<Team> entityQuery =
        new EntityQuery<>(database, EntityType.TEAM, List.copyOf(filters));
    return new GameEntityJoin<>(entityQuery, joinCondition);
  }

  private @NotNull GameEntityJoin<GameTag> buildGameTagJoin(
      @NotNull Database database, @NotNull List<FilterCondition> conditions) {
    List<EntityFilter<GameTag>> filters = new ArrayList<>();

    for (FilterCondition condition : conditions) {
      String property = condition.field().split("\\.", 2)[1].toLowerCase();
      if (!"title".equals(property) && !"name".equals(property)) {
        throw new IllegalArgumentException("Unknown game tag property: " + property);
      }
      filters.add(new GameTagTitleFilter(condition.value(), false, false));
    }

    EntityQuery<GameTag> entityQuery =
        new EntityQuery<>(database, EntityType.GAME_TAG, List.copyOf(filters));
    return new GameEntityJoin<>(entityQuery, null);
  }

  private @NotNull GameFilter buildDateFilter(@NotNull FilterCondition condition) {
    if ("..".equals(condition.operator())) {
      // Range operator: date..2020..2025
      String[] parts = condition.value().split("\\.\\.", 2);
      Date from = parts[0].isEmpty() ? Date.unset() : parseDateStart(parts[0]);
      Date to = parts.length > 1 && !parts[1].isEmpty() ? parseDateEnd(parts[1]) : Date.unset();
      return new DateRangeFilter(from, to);
    } else {
      // Colon operator: may contain embedded range
      String value = condition.value();
      if (value.contains("..")) {
        // "2020..2025" or "2020.." or "..2025"
        String[] parts = value.split("\\.\\.", 2);
        Date from = parts[0].isEmpty() ? Date.unset() : parseDateStart(parts[0]);
        Date to = parts.length > 1 && !parts[1].isEmpty() ? parseDateEnd(parts[1]) : Date.unset();
        return new DateRangeFilter(from, to);
      }
      // Single value: partial date expansion
      PartialDateParser.DateRange range = PartialDateParser.parse(value);
      return new DateRangeFilter(range.from(), range.to());
    }
  }

  private @NotNull GameFilter buildRatingFilter(@NotNull FilterCondition condition) {
    String mode = condition.modifiers().getOrDefault("mode", "any");
    RatingRangeFilter.RatingColor ratingColor = parseRatingMode(mode);

    if ("..".equals(condition.operator())) {
      String[] parts = condition.value().split("\\.\\.", 2);
      int min = parts[0].isEmpty() ? 0 : Integer.parseInt(parts[0]);
      int max = parts.length > 1 && !parts[1].isEmpty() ? Integer.parseInt(parts[1]) : 9999;
      return new RatingRangeFilter(min, max, ratingColor);
    } else {
      String value = condition.value();
      if (value.contains("..")) {
        String[] parts = value.split("\\.\\.", 2);
        int min = parts[0].isEmpty() ? 0 : Integer.parseInt(parts[0]);
        int max = parts.length > 1 && !parts[1].isEmpty() ? Integer.parseInt(parts[1]) : 9999;
        return new RatingRangeFilter(min, max, ratingColor);
      }
      int rating = Integer.parseInt(value);
      return new RatingRangeFilter(rating, rating, ratingColor);
    }
  }

  private @NotNull GameFilter buildRoundFilter(@NotNull FilterCondition condition) {
    String value = condition.value();

    String subRoundStr = condition.modifiers().get("subround");
    if (subRoundStr != null) {
      int round = Integer.parseInt(value);
      int subRound = Integer.parseInt(subRoundStr);
      return new RoundFilter(round, subRound);
    }

    if (value.contains(".")) {
      String[] parts = value.split("\\.", 2);
      int round = Integer.parseInt(parts[0]);
      int subRound = Integer.parseInt(parts[1]);
      return new RoundFilter(round, subRound);
    }

    int round = Integer.parseInt(value);
    return new RoundFilter(round);
  }

  private @NotNull GameFilter buildTypeFilter(@NotNull FilterCondition condition) {
    return switch (condition.value().toLowerCase()) {
      case "game" -> new IsGameFilter();
      case "text" -> new TextStorageFilter();
      default ->
          throw new IllegalArgumentException(
              "Unknown game type: " + condition.value() + ". Expected 'game' or 'text'");
    };
  }

  private @NotNull GameEntityJoinCondition parsePlayerPosition(@NotNull String position) {
    return switch (position.toLowerCase()) {
      case "any" -> GameEntityJoinCondition.ANY;
      case "both" -> GameEntityJoinCondition.BOTH;
      case "white" -> GameEntityJoinCondition.WHITE;
      case "black" -> GameEntityJoinCondition.BLACK;
      case "winner" -> GameEntityJoinCondition.WINNER;
      case "loser" -> GameEntityJoinCondition.LOSER;
      default -> throw new IllegalArgumentException("Invalid player position: " + position);
    };
  }

  private @NotNull GameEntityJoinCondition parseTeamPosition(@NotNull String position) {
    return switch (position.toLowerCase()) {
      case "any" -> GameEntityJoinCondition.ANY;
      case "white" -> GameEntityJoinCondition.WHITE;
      case "black" -> GameEntityJoinCondition.BLACK;
      case "winner" -> GameEntityJoinCondition.WINNER;
      case "loser" -> GameEntityJoinCondition.LOSER;
      default -> throw new IllegalArgumentException("Invalid team position: " + position);
    };
  }

  private @NotNull RatingRangeFilter.RatingColor parseRatingMode(@NotNull String mode) {
    return switch (mode.toLowerCase()) {
      case "any" -> RatingRangeFilter.RatingColor.ANY;
      case "both" -> RatingRangeFilter.RatingColor.BOTH;
      case "white" -> RatingRangeFilter.RatingColor.WHITE;
      case "black" -> RatingRangeFilter.RatingColor.BLACK;
      case "average" -> RatingRangeFilter.RatingColor.AVERAGE;
      case "difference" -> RatingRangeFilter.RatingColor.DIFFERENCE;
      default -> throw new IllegalArgumentException("Invalid rating mode: " + mode);
    };
  }

  /** Parses a date string and returns the start of the range. */
  private @NotNull Date parseDateStart(@NotNull String dateStr) {
    return PartialDateParser.parse(dateStr).from();
  }

  /** Parses a date string and returns the end of the range. */
  private @NotNull Date parseDateEnd(@NotNull String dateStr) {
    return PartialDateParser.parse(dateStr).to();
  }
}

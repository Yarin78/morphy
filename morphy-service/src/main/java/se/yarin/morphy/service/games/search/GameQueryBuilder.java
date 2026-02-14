package se.yarin.morphy.service.games.search;

import java.time.LocalDate;
import java.util.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import se.yarin.chess.Date;
import se.yarin.morphy.Database;
import se.yarin.morphy.Game;
import se.yarin.morphy.entities.*;
import se.yarin.morphy.entities.filters.*;
import se.yarin.morphy.games.filters.*;
import se.yarin.morphy.queries.*;
import se.yarin.morphy.service.games.dto.GameSearchRequest;

/**
 * Converts GameSearchRequest into GameQuery objects for use with the QueryPlanner.
 *
 * <p>Handles both typed parameters (playerId, result, etc.) and query language filters
 * (filter="result:1-0 AND player.name:Carlsen").
 *
 * <p>Game property filters (result, date, rating, eco, round) become GameFilter objects. Entity ID
 * filters (playerId, tournamentId, etc.) become GameFilter objects. Entity property filters
 * (player.name, tournament.title, etc.) become EntityQuery + GameEntityJoin objects.
 */
@Component
public class GameQueryBuilder {
  private static final Logger log = LoggerFactory.getLogger(GameQueryBuilder.class);

  private final FilterQueryParser filterQueryParser;

  public GameQueryBuilder(FilterQueryParser filterQueryParser) {
    this.filterQueryParser = filterQueryParser;
  }

  /**
   * Builds a GameQuery from a GameSearchRequest.
   *
   * @param database the database to query
   * @param request the search request with filters and sort options
   * @return GameQuery ready for execution via QueryPlanner
   */
  public @NotNull GameQuery buildQuery(
      @NotNull Database database, @NotNull GameSearchRequest request) {
    // 1. Parse all conditions (typed + query language)
    List<FilterCondition> conditions = parseAllConditions(request);

    // 2. Separate conditions by type
    List<GameFilter> gameFilters = new ArrayList<>();
    List<GameEntityJoin<?>> entityJoins = new ArrayList<>();

    for (FilterCondition condition : conditions) {
      if (isGamePropertyFilter(condition)) {
        gameFilters.add(buildGameFilter(condition));
      } else if (isEntityIdFilter(condition)) {
        gameFilters.add(buildEntityIdFilter(condition));
      } else if (isEntityPropertyFilter(condition)) {
        entityJoins.add(buildEntityJoin(database, condition));
      } else {
        throw new IllegalArgumentException("Unknown filter type: " + condition.field());
      }
    }

    // 3. Determine sort order
    QuerySortOrder<Game> sortOrder = buildSortOrder(request);

    // 4. Build GameQuery (limit=0 means no limit at query level; pagination applied post-query)
    return new GameQuery(database, gameFilters, entityJoins, sortOrder, 0);
  }

  /**
   * Parses all filter conditions from both typed parameters and query language.
   *
   * @param request the search request
   * @return list of all filter conditions
   */
  private @NotNull List<FilterCondition> parseAllConditions(@NotNull GameSearchRequest request) {
    List<FilterCondition> conditions = new ArrayList<>();

    // Parse query language filter
    if (request.filter() != null && !request.filter().isBlank()) {
      conditions.addAll(filterQueryParser.parse(request.filter()));
    }

    // Add typed parameter filters
    if (request.result() != null) {
      conditions.add(new FilterCondition("result", ":", request.result()));
    }

    if (request.dateFrom() != null || request.dateTo() != null) {
      String from = request.dateFrom() != null ? formatLocalDate(request.dateFrom()) : "";
      String to = request.dateTo() != null ? formatLocalDate(request.dateTo()) : "";
      conditions.add(new FilterCondition("date", "..", from + ".." + to));
    }

    if (request.ecoCode() != null) {
      conditions.add(new FilterCondition("eco", ":", request.ecoCode()));
    }

    if (request.round() != null) {
      conditions.add(new FilterCondition("round", ":", request.round().toString()));
    }

    if (request.ratingMin() != null || request.ratingMax() != null) {
      int min = request.ratingMin() != null ? request.ratingMin() : 0;
      int max = request.ratingMax() != null ? request.ratingMax() : 9999;
      Map<String, String> modifiers =
          Map.of("mode", request.ratingMode() != null ? request.ratingMode() : "any");
      conditions.add(new FilterCondition("rating", "..", min + ".." + max, modifiers));
    }

    if (request.playerId() != null) {
      Map<String, String> modifiers =
          Map.of(
              "position",
              request.playerPosition() != null ? request.playerPosition() : "any");
      conditions.add(
          new FilterCondition("playerId", ":", request.playerId().toString(), modifiers));
    }

    if (request.tournamentId() != null) {
      conditions.add(new FilterCondition("tournamentId", ":", request.tournamentId().toString()));
    }

    if (request.annotatorId() != null) {
      conditions.add(new FilterCondition("annotatorId", ":", request.annotatorId().toString()));
    }

    if (request.sourceId() != null) {
      conditions.add(new FilterCondition("sourceId", ":", request.sourceId().toString()));
    }

    if (request.teamId() != null) {
      Map<String, String> modifiers =
          Map.of("position", request.teamPosition() != null ? request.teamPosition() : "any");
      conditions.add(new FilterCondition("teamId", ":", request.teamId().toString(), modifiers));
    }

    if (request.gameTagId() != null) {
      conditions.add(new FilterCondition("gameTagId", ":", request.gameTagId().toString()));
    }

    return conditions;
  }

  /**
   * Checks if a condition represents a game property filter (result, date, rating, eco, round).
   */
  private boolean isGamePropertyFilter(@NotNull FilterCondition condition) {
    String field = condition.field().toLowerCase();
    return field.equals("result")
        || field.equals("date")
        || field.equals("rating")
        || field.equals("eco")
        || field.equals("round");
  }

  /**
   * Checks if a condition represents an entity ID filter (playerId, tournamentId, etc.).
   */
  private boolean isEntityIdFilter(@NotNull FilterCondition condition) {
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

  /**
   * Checks if a condition represents an entity property filter (player.name, tournament.title,
   * etc.).
   */
  private boolean isEntityPropertyFilter(@NotNull FilterCondition condition) {
    return condition.field().contains(".");
  }

  /**
   * Builds a GameFilter for game properties (result, date, rating, eco, round).
   */
  private @NotNull GameFilter buildGameFilter(@NotNull FilterCondition condition) {
    return switch (condition.field().toLowerCase()) {
      case "result" -> new ResultsFilter(condition.value());
      case "date" -> buildDateFilter(condition);
      case "rating" -> buildRatingFilter(condition);
      case "eco" -> new EcoFilter(condition.value());
      case "round" -> buildRoundFilter(condition);
      default ->
          throw new IllegalArgumentException("Unknown game filter: " + condition.field());
    };
  }

  /**
   * Builds a GameFilter for entity IDs (playerId, tournamentId, etc.).
   */
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
   * Builds a GameEntityJoin for entity properties (player.name, tournament.title, etc.).
   */
  private @NotNull GameEntityJoin<?> buildEntityJoin(
      @NotNull Database database, @NotNull FilterCondition condition) {
    String[] parts = condition.field().split("\\.", 2);
    if (parts.length != 2) {
      throw new IllegalArgumentException(
          "Invalid entity property filter: " + condition.field() + ". Expected format: entity.property");
    }

    String entityType = parts[0];
    String property = parts[1];

    return switch (entityType.toLowerCase()) {
      case "player" -> buildPlayerJoin(database, property, condition);
      case "tournament" -> buildTournamentJoin(database, property, condition);
      case "annotator" -> buildAnnotatorJoin(database, property, condition);
      case "source" -> buildSourceJoin(database, property, condition);
      case "team" -> buildTeamJoin(database, property, condition);
      case "gametag" -> buildGameTagJoin(database, property, condition);
      default ->
          throw new IllegalArgumentException("Unknown entity type: " + entityType);
    };
  }

  /**
   * Builds a GameEntityJoin for player entity with PlayerNameFilter.
   */
  private @NotNull GameEntityJoin<Player> buildPlayerJoin(
      @NotNull Database database, @NotNull String property, @NotNull FilterCondition condition) {
    if (!"name".equalsIgnoreCase(property)) {
      throw new IllegalArgumentException("Unknown player property: " + property);
    }

    // PlayerNameFilter: case-insensitive substring matching
    PlayerNameFilter filter = new PlayerNameFilter(condition.value(), false, false);
    EntityQuery<Player> entityQuery =
        new EntityQuery<>(database, EntityType.PLAYER, List.of(filter));

    String position = condition.modifiers().getOrDefault("position", "any");
    return new GameEntityJoin<>(entityQuery, parsePlayerPosition(position));
  }

  /**
   * Builds a GameEntityJoin for tournament entity.
   */
  private @NotNull GameEntityJoin<Tournament> buildTournamentJoin(
      @NotNull Database database, @NotNull String property, @NotNull FilterCondition condition) {
    EntityFilter<Tournament> filter =
        switch (property.toLowerCase()) {
          case "title", "name" ->
              new TournamentTitleFilter(condition.value(), false, false); // case-insensitive substring
          case "place" -> new TournamentPlaceFilter(condition.value(), false, false); // case-insensitive substring
          case "year" -> {
            int year = Integer.parseInt(condition.value());
            yield new TournamentStartDateFilter(
                new Date(year, 1, 1), new Date(year, 12, 31));
          }
          default ->
              throw new IllegalArgumentException("Unknown tournament property: " + property);
        };

    EntityQuery<Tournament> entityQuery =
        new EntityQuery<>(database, EntityType.TOURNAMENT, List.of(filter));

    return new GameEntityJoin<>(entityQuery, null); // No position for tournaments
  }

  /**
   * Builds a GameEntityJoin for annotator entity.
   */
  private @NotNull GameEntityJoin<Annotator> buildAnnotatorJoin(
      @NotNull Database database, @NotNull String property, @NotNull FilterCondition condition) {
    if (!"name".equalsIgnoreCase(property)) {
      throw new IllegalArgumentException("Unknown annotator property: " + property);
    }

    AnnotatorNameFilter filter = new AnnotatorNameFilter(condition.value(), false, false);
    EntityQuery<Annotator> entityQuery =
        new EntityQuery<>(database, EntityType.ANNOTATOR, List.of(filter));

    return new GameEntityJoin<>(entityQuery, null);
  }

  /**
   * Builds a GameEntityJoin for source entity.
   */
  private @NotNull GameEntityJoin<Source> buildSourceJoin(
      @NotNull Database database, @NotNull String property, @NotNull FilterCondition condition) {
    if (!"title".equalsIgnoreCase(property) && !"name".equalsIgnoreCase(property)) {
      throw new IllegalArgumentException("Unknown source property: " + property);
    }

    SourceTitleFilter filter = new SourceTitleFilter(condition.value(), false, false);
    EntityQuery<Source> entityQuery =
        new EntityQuery<>(database, EntityType.SOURCE, List.of(filter));

    return new GameEntityJoin<>(entityQuery, null);
  }

  /**
   * Builds a GameEntityJoin for team entity.
   */
  private @NotNull GameEntityJoin<Team> buildTeamJoin(
      @NotNull Database database, @NotNull String property, @NotNull FilterCondition condition) {
    if (!"title".equalsIgnoreCase(property) && !"name".equalsIgnoreCase(property)) {
      throw new IllegalArgumentException("Unknown team property: " + property);
    }

    TeamTitleFilter filter = new TeamTitleFilter(condition.value(), false, false);
    EntityQuery<Team> entityQuery =
        new EntityQuery<>(database, EntityType.TEAM, List.of(filter));

    String position = condition.modifiers().getOrDefault("position", "any");
    return new GameEntityJoin<>(entityQuery, parseTeamPosition(position));
  }

  /**
   * Builds a GameEntityJoin for game tag entity.
   */
  private @NotNull GameEntityJoin<GameTag> buildGameTagJoin(
      @NotNull Database database, @NotNull String property, @NotNull FilterCondition condition) {
    if (!"title".equalsIgnoreCase(property) && !"name".equalsIgnoreCase(property)) {
      throw new IllegalArgumentException("Unknown game tag property: " + property);
    }

    GameTagTitleFilter filter = new GameTagTitleFilter(condition.value(), false, false);
    EntityQuery<GameTag> entityQuery =
        new EntityQuery<>(database, EntityType.GAME_TAG, List.of(filter));

    return new GameEntityJoin<>(entityQuery, null);
  }

  /**
   * Builds a QuerySortOrder for the query. Only ID and date sorting are supported at the query
   * level. Rating-based sorting must be done post-query.
   */
  private @NotNull QuerySortOrder<Game> buildSortOrder(@NotNull GameSearchRequest request) {
    String sortBy = request.sortBy().toLowerCase();
    boolean reverse = "desc".equalsIgnoreCase(request.order());

    return switch (sortBy) {
      case "id" -> QuerySortOrder.byId();
      case "date" -> {
        QuerySortOrder.Direction direction =
            reverse ? QuerySortOrder.Direction.DESCENDING : QuerySortOrder.Direction.ASCENDING;
        yield new QuerySortOrder<>(
            List.of(QuerySortField.playedDate()),
            List.of(direction));
      }
      // whiteElo, blackElo, avgElo sorting done post-query in service layer
      default -> QuerySortOrder.none();
    };
  }

  /**
   * Parses player position string into GameEntityJoinCondition.
   */
  private @NotNull GameEntityJoinCondition parsePlayerPosition(@NotNull String position) {
    return switch (position.toLowerCase()) {
      case "any" -> GameEntityJoinCondition.ANY;
      case "both" -> GameEntityJoinCondition.BOTH;
      case "white" -> GameEntityJoinCondition.WHITE;
      case "black" -> GameEntityJoinCondition.BLACK;
      case "winner" -> GameEntityJoinCondition.WINNER;
      case "loser" -> GameEntityJoinCondition.LOSER;
      default ->
          throw new IllegalArgumentException("Invalid player position: " + position);
    };
  }

  /**
   * Parses team position string into GameEntityJoinCondition.
   */
  private @NotNull GameEntityJoinCondition parseTeamPosition(@NotNull String position) {
    return switch (position.toLowerCase()) {
      case "any" -> GameEntityJoinCondition.ANY;
      case "white" -> GameEntityJoinCondition.WHITE;
      case "black" -> GameEntityJoinCondition.BLACK;
      case "winner" -> GameEntityJoinCondition.WINNER;
      case "loser" -> GameEntityJoinCondition.LOSER;
      default ->
          throw new IllegalArgumentException("Invalid team position: " + position);
    };
  }

  /**
   * Builds a date filter with support for partial dates.
   */
  private @NotNull GameFilter buildDateFilter(@NotNull FilterCondition condition) {
    if ("..".equals(condition.operator())) {
      // Range: "2020..2025" or "..2025" or "2020.."
      String[] parts = condition.value().split("\\.\\.", 2);
      Date from = parts[0].isEmpty() ? Date.unset() : parseDate(parts[0]);
      Date to = parts[1].isEmpty() ? Date.unset() : parseDate(parts[1]);
      return new DateRangeFilter(from, to);
    } else {
      // Single value with partial date expansion (e.g., "2020" → entire year)
      PartialDateParser.DateRange range = PartialDateParser.parse(condition.value());
      return new DateRangeFilter(range.from(), range.to());
    }
  }

  /**
   * Builds a rating filter with support for range syntax.
   */
  private @NotNull GameFilter buildRatingFilter(@NotNull FilterCondition condition) {
    String mode = condition.modifiers().getOrDefault("mode", "any");
    RatingRangeFilter.RatingColor ratingMode = parseRatingMode(mode);

    if ("..".equals(condition.operator())) {
      // Range: "2600.." or "..2700" or "2600..2700"
      String[] parts = condition.value().split("\\.\\.", 2);
      int min = parts[0].isEmpty() ? 0 : Integer.parseInt(parts[0]);
      int max = parts[1].isEmpty() ? 9999 : Integer.parseInt(parts[1]);
      return new RatingRangeFilter(min, max, ratingMode);
    } else {
      // Single value: exact rating
      int rating = Integer.parseInt(condition.value());
      return new RatingRangeFilter(rating, rating, ratingMode);
    }
  }

  /**
   * Parses rating mode string into RatingColor enum.
   */
  private @NotNull RatingRangeFilter.RatingColor parseRatingMode(@NotNull String mode) {
    return switch (mode.toLowerCase()) {
      case "any" -> RatingRangeFilter.RatingColor.ANY;
      case "both" -> RatingRangeFilter.RatingColor.BOTH;
      case "white" -> RatingRangeFilter.RatingColor.WHITE;
      case "black" -> RatingRangeFilter.RatingColor.BLACK;
      case "average" -> RatingRangeFilter.RatingColor.AVERAGE;
      case "difference" -> RatingRangeFilter.RatingColor.DIFFERENCE;
      default ->
          throw new IllegalArgumentException("Invalid rating mode: " + mode);
    };
  }

  /**
   * Builds a round filter with optional sub-round support.
   */
  private @NotNull GameFilter buildRoundFilter(@NotNull FilterCondition condition) {
    String value = condition.value();

    // Check if sub-round is specified via modifier
    String subRoundStr = condition.modifiers().get("subround");
    if (subRoundStr != null) {
      int round = Integer.parseInt(value);
      int subRound = Integer.parseInt(subRoundStr);
      return new RoundFilter(round, subRound);
    }

    // Check if value contains sub-round in "round.subround" format
    if (value.contains(".")) {
      String[] parts = value.split("\\.", 2);
      int round = Integer.parseInt(parts[0]);
      int subRound = Integer.parseInt(parts[1]);
      return new RoundFilter(round, subRound);
    }

    // Just round number
    int round = Integer.parseInt(value);
    return new RoundFilter(round);
  }

  /**
   * Parses a date string (YYYY, YYYY-MM, or YYYY-MM-DD) into a Date object.
   */
  private @NotNull Date parseDate(@NotNull String dateStr) {
    PartialDateParser.DateRange range = PartialDateParser.parse(dateStr);
    return range.from(); // Use start date for single date values
  }

  /**
   * Formats a LocalDate as YYYY-MM-DD for use in filter conditions.
   */
  private @NotNull String formatLocalDate(@NotNull LocalDate date) {
    return String.format("%04d-%02d-%02d", date.getYear(), date.getMonthValue(), date.getDayOfMonth());
  }
}

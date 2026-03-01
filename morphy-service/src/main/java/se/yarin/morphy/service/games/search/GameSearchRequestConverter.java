package se.yarin.morphy.service.games.search;

import java.time.LocalDate;
import java.util.*;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import se.yarin.morphy.Database;
import se.yarin.morphy.Game;
import se.yarin.morphy.queries.*;
import se.yarin.morphy.queries.filter.FilterCondition;
import se.yarin.morphy.queries.filter.FilterQueryParser;
import se.yarin.morphy.queries.filter.GameQueryBuilder;
import se.yarin.morphy.service.games.dto.GameSearchRequest;

/**
 * Converts {@link GameSearchRequest} typed parameters into {@link FilterCondition}s and delegates
 * query building to the core {@link GameQueryBuilder}.
 */
@Component
public class GameSearchRequestConverter {

  private final GameQueryBuilder coreBuilder = new GameQueryBuilder();
  private final FilterQueryParser filterQueryParser = new FilterQueryParser("player.name");

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

    // 2. Delegate to core builder
    GameQuery baseQuery = coreBuilder.buildQuery(database, conditions);

    // 3. Apply sort order (service-specific concern)
    QuerySortOrder<Game> sortOrder = buildSortOrder(request);

    return new GameQuery(
        database,
        baseQuery.gameFilters(),
        new ArrayList<>(baseQuery.entityJoins()),
        sortOrder,
        0);
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
   * Builds a QuerySortOrder for the query. Only ID and date sorting are supported at the query
   * level. Rating-based sorting must be done post-query. sortBy uses +/- prefix for direction
   * (e.g. "+id", "-date").
   */
  private @NotNull QuerySortOrder<Game> buildSortOrder(@NotNull GameSearchRequest request) {
    String sortBy = request.sortBy().trim();
    boolean descending = sortBy.startsWith("-");
    if (sortBy.startsWith("+") || sortBy.startsWith("-")) {
      sortBy = sortBy.substring(1).trim();
    }
    sortBy = sortBy.toLowerCase();

    QuerySortOrder.Direction direction =
        descending ? QuerySortOrder.Direction.DESCENDING : QuerySortOrder.Direction.ASCENDING;

    return switch (sortBy) {
      case "id" -> new QuerySortOrder<>(List.of(QuerySortField.id()), List.of(direction));
      case "date", "playeddate" ->
          new QuerySortOrder<>(List.of(QuerySortField.playedDate()), List.of(direction));
      case "whiteplayername" ->
          new QuerySortOrder<>(List.of(QuerySortField.gameWhitePlayerName()), List.of(direction));
      case "blackplayername" ->
          new QuerySortOrder<>(List.of(QuerySortField.gameBlackPlayerName()), List.of(direction));
      case "result" ->
          new QuerySortOrder<>(List.of(QuerySortField.gameResult()), List.of(direction));
      case "eco" ->
          new QuerySortOrder<>(List.of(QuerySortField.gameEco()), List.of(direction));
      case "round" ->
          new QuerySortOrder<>(List.of(QuerySortField.gameRound()), List.of(direction));
      case "tournament" ->
          new QuerySortOrder<>(List.of(QuerySortField.gameTournamentTitle()), List.of(direction));
      case "source" ->
          new QuerySortOrder<>(List.of(QuerySortField.gameSourceTitle()), List.of(direction));
      case "annotator" ->
          new QuerySortOrder<>(List.of(QuerySortField.gameAnnotatorName()), List.of(direction));
      case "gametag" ->
          new QuerySortOrder<>(List.of(QuerySortField.gameGameTagTitle()), List.of(direction));
      case "whiteelo" ->
          new QuerySortOrder<>(List.of(QuerySortField.gameWhiteElo()), List.of(direction));
      case "blackelo" ->
          new QuerySortOrder<>(List.of(QuerySortField.gameBlackElo()), List.of(direction));
      case "nomoves" ->
          new QuerySortOrder<>(List.of(QuerySortField.gameNoMoves()), List.of(direction));
      case "whiteteam" ->
          new QuerySortOrder<>(List.of(QuerySortField.gameWhiteTeamTitle()), List.of(direction));
      case "blackteam" ->
          new QuerySortOrder<>(List.of(QuerySortField.gameBlackTeamTitle()), List.of(direction));
      case "setupposition" ->
          new QuerySortOrder<>(List.of(QuerySortField.gameSetupPosition()), List.of(direction));
      case "topgame" ->
          new QuerySortOrder<>(List.of(QuerySortField.gameTopGame()), List.of(direction));
      case "ait" ->
          new QuerySortOrder<>(List.of(QuerySortField.gameAit()), List.of(direction));
      case "medals" ->
          new QuerySortOrder<>(List.of(QuerySortField.gameMedals()), List.of(direction));
      case "vcs" ->
          new QuerySortOrder<>(List.of(QuerySortField.gameVcs()), List.of(direction));
      case "finalmaterial" ->
          new QuerySortOrder<>(List.of(QuerySortField.gameFinalMaterial()), List.of(direction));
      case "gameversion" ->
          new QuerySortOrder<>(List.of(QuerySortField.gameVersion()), List.of(direction));
      case "creationtimestamp" ->
          new QuerySortOrder<>(List.of(QuerySortField.gameCreationTimestamp()), List.of(direction));
      case "lastchanged" ->
          new QuerySortOrder<>(List.of(QuerySortField.gameLastChanged()), List.of(direction));
      case "playedyear" ->
          new QuerySortOrder<>(List.of(QuerySortField.gamePlayedYear()), List.of(direction));
      case "eloavg" ->
          new QuerySortOrder<>(List.of(QuerySortField.gameEloAvg()), List.of(direction));
      case "elomax" ->
          new QuerySortOrder<>(List.of(QuerySortField.gameEloMax()), List.of(direction));
      case "notation" ->
          new QuerySortOrder<>(List.of(QuerySortField.gameNotation()), List.of(direction));
      case "variationmoves" ->
          new QuerySortOrder<>(List.of(QuerySortField.gameVariationMoves()), List.of(direction));
      default -> QuerySortOrder.none();
    };
  }

  /**
   * Formats a LocalDate as YYYY-MM-DD for use in filter conditions.
   */
  private @NotNull String formatLocalDate(@NotNull LocalDate date) {
    return String.format(
        "%04d-%02d-%02d", date.getYear(), date.getMonthValue(), date.getDayOfMonth());
  }
}

package se.yarin.morphy.service.games.search;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import se.yarin.morphy.api.query.FilterCondition;
import se.yarin.morphy.api.query.Query;
import se.yarin.morphy.api.query.Sort;
import se.yarin.morphy.service.games.dto.GameSearchRequest;

/**
 * Turns a {@link GameSearchRequest} into a {@link Query} for {@link
 * se.yarin.morphy.api.Database#findGames}. The free-text filter is passed on as it is, for the
 * database to parse; the typed parameters become extra {@link FilterCondition}s.
 */
@Component
public class GameSearchRequestConverter {

  /** The largest page a search may ask for. */
  public static final int MAX_LIMIT = 1000;

  /**
   * Builds the query for a search request.
   *
   * @param request the search request
   * @return the query; the window is clamped to at most {@link #MAX_LIMIT} games
   */
  public @NotNull Query toQuery(@NotNull GameSearchRequest request) {
    int offset = Math.max(0, request.offset());
    int limit = Math.min(MAX_LIMIT, Math.max(1, request.limit()));
    return new Query(
        request.filter(), typedConditions(request), Sort.parse(request.sortBy()), offset, limit);
  }

  /**
   * Turns the typed request parameters into filter conditions.
   *
   * @param request the search request
   * @return the conditions, in addition to the free-text filter
   */
  private @NotNull List<FilterCondition> typedConditions(@NotNull GameSearchRequest request) {
    List<FilterCondition> conditions = new ArrayList<>();

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
   * Formats a LocalDate as YYYY-MM-DD for use in filter conditions.
   */
  private @NotNull String formatLocalDate(@NotNull LocalDate date) {
    return String.format(
        "%04d-%02d-%02d", date.getYear(), date.getMonthValue(), date.getDayOfMonth());
  }
}

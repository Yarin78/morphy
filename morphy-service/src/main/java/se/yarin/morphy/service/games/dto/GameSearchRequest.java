package se.yarin.morphy.service.games.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import org.jetbrains.annotations.Nullable;

/**
 * Request model for searching games.
 *
 * <p>Supports both a complex filter query language and simple typed parameters for common use
 * cases.
 *
 * <p>Examples:
 *
 * <ul>
 *   <li>Simple: ?result=1-0&ratingMin=2600&playerId=123
 *   <li>Complex: ?filter=result:1-0 AND rating:2600.. AND player.name:Carlsen
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GameSearchRequest(
    // Pagination
    @Nullable Integer offset, // Skip N games (default 0)
    @Nullable Integer limit, // Return max N games (default 50, max 1000)

    // Sorting
    @Nullable String sortBy, // "id", "date", "whiteElo", "blackElo", "avgElo" (default "id")
    @Nullable String order, // "asc", "desc" (default "asc")

    // Content control
    @Nullable Boolean includeMoves, // Include PGN moves (default false)
    @Nullable Boolean includeText, // Include commentary (default false)

    // Complex filter (query language)
    @Nullable String filter,

    // Simple typed filters (for common cases)
    @Nullable String result, // "1-0", "0-1", "1/2-1/2", etc.
    @Nullable LocalDate dateFrom,
    @Nullable LocalDate dateTo,
    @Nullable String ecoCode, // Supports wildcards like "B9*"
    @Nullable Integer round,
    @Nullable Integer ratingMin,
    @Nullable Integer ratingMax,
    @Nullable String ratingMode, // "any", "both", "white", "black", "average", "difference"
    @Nullable Integer playerId,
    @Nullable String playerPosition, // "white", "black", "any", "both", "winner", "loser"
    @Nullable Integer tournamentId,
    @Nullable Integer annotatorId,
    @Nullable Integer sourceId,
    @Nullable Integer teamId,
    @Nullable String teamPosition, // "white", "black", "any", "winner", "loser"
    @Nullable Integer gameTagId) {

  public GameSearchRequest {
    // Set defaults for null values
    if (offset == null) {
      offset = 0;
    }
    if (limit == null) {
      limit = 50;
    }
    if (sortBy == null || sortBy.isBlank()) {
      sortBy = "id";
    }
    if (order == null || order.isBlank()) {
      order = "asc";
    }
    if (includeMoves == null) {
      includeMoves = false;
    }
    if (includeText == null) {
      includeText = false;
    }
    if (ratingMode == null || ratingMode.isBlank()) {
      ratingMode = "any";
    }
    if (playerPosition == null || playerPosition.isBlank()) {
      playerPosition = "any";
    }
    if (teamPosition == null || teamPosition.isBlank()) {
      teamPosition = "any";
    }
  }
}

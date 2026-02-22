package se.yarin.morphy.service.tournaments;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import se.yarin.morphy.service.MorphyServiceException;
import se.yarin.morphy.service.search.EntitySearchRequest;
import se.yarin.morphy.service.search.EntitySearchResponse;
import se.yarin.morphy.service.tournaments.dto.TournamentDto;

@RestController
@RequestMapping("/api/databases/{databaseId}/tournaments")
public class TournamentsController {
  private static final Logger log = LoggerFactory.getLogger(TournamentsController.class);

  private final @NotNull TournamentsService tournamentsService;

  public TournamentsController(@NotNull TournamentsService tournamentsService) {
    this.tournamentsService = tournamentsService;
  }

  /**
   * Get tournaments with cursor-based pagination.
   *
   * @param databaseId The database ID
   * @param cursor Optional cursor for pagination (tournament ID to start from)
   * @param limit Number of tournaments to return (default 100, max 1000)
   * @return Paginated list of tournaments
   */
  @GetMapping
  public ResponseEntity<TournamentListResponse> getTournaments(
      @PathVariable String databaseId,
      @RequestParam(required = false) Integer cursor,
      @RequestParam(defaultValue = "100") int limit) {
    TournamentListResponse response = tournamentsService.getTournaments(databaseId, cursor, limit);
    return ResponseEntity.ok(response);
  }

  /**
   * Get a single tournament by ID.
   *
   * @param databaseId The database ID
   * @param tournamentId The tournament ID
   * @return The tournament information, or 404 if not found
   */
  @GetMapping("/{tournamentId}")
  public ResponseEntity<TournamentDto> getTournament(
      @PathVariable String databaseId, @PathVariable int tournamentId) {
    try {
      TournamentDto tournament = tournamentsService.getTournament(databaseId, tournamentId);
      if (tournament == null) {
        return ResponseEntity.notFound().build();
      }
      return ResponseEntity.ok(tournament);
    } catch (MorphyServiceException e) {
      log.error(
          "Error retrieving tournament {} from database '{}': {}",
          tournamentId,
          databaseId,
          e.getMessage());
      return ResponseEntity.internalServerError().build();
    }
  }

  /**
   * Get the total count of tournaments in the database.
   *
   * @param databaseId The database ID
   * @return The count of tournaments
   */
  @GetMapping("/count")
  public ResponseEntity<TournamentCountResponse> getTournamentCount(
      @PathVariable String databaseId) {
    int count = tournamentsService.getTournamentCount(databaseId);
    return ResponseEntity.ok(new TournamentCountResponse(count));
  }

  /**
   * Update an existing tournament in the database.
   *
   * @param databaseId The database ID
   * @param tournamentId The ID of the tournament to update
   * @param tournamentDto The new tournament data
   * @return The updated tournament
   */
  @PutMapping("/{tournamentId}")
  public ResponseEntity<TournamentDto> updateTournament(
      @PathVariable String databaseId,
      @PathVariable int tournamentId,
      @RequestBody TournamentDto tournamentDto) {
    try {
      TournamentDto updatedTournament =
          tournamentsService.updateTournament(databaseId, tournamentId, tournamentDto);
      return ResponseEntity.ok(updatedTournament);
    } catch (MorphyServiceException e) {
      log.error(
          "Error updating tournament {} in database '{}': {}",
          tournamentId,
          databaseId,
          e.getMessage());
      return ResponseEntity.internalServerError().build();
    } catch (Exception e) {
      log.error(
          "Unexpected error updating tournament {} in database '{}'", tournamentId, databaseId, e);
      return ResponseEntity.notFound().build();
    }
  }

  @GetMapping("/search")
  public ResponseEntity<EntitySearchResponse<TournamentDto>> searchTournaments(
      @PathVariable String databaseId,
      @RequestParam(required = false) String filter,
      @RequestParam(required = false) Integer offset,
      @RequestParam(required = false) Integer limit,
      @RequestParam(required = false) String sortBy,
      @RequestParam(required = false) Boolean debugQueryPlans,
      @RequestParam(required = false) Boolean debugExecuteAllPlans,
      @RequestParam(required = false) Boolean debugRawData) {
    EntitySearchRequest request =
        new EntitySearchRequest(filter, offset, limit, sortBy, debugQueryPlans,
            debugExecuteAllPlans, debugRawData);
    EntitySearchResponse<TournamentDto> response =
        tournamentsService.searchTournaments(databaseId, request);
    return ResponseEntity.ok(response);
  }
}

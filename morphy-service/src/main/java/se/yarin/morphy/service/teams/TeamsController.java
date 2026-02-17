package se.yarin.morphy.service.teams;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import se.yarin.morphy.service.MorphyServiceException;
import se.yarin.morphy.service.search.EntitySearchRequest;
import se.yarin.morphy.service.search.EntitySearchResponse;
import se.yarin.morphy.service.teams.dto.TeamDto;

@RestController
@RequestMapping("/api/databases/{databaseId}/teams")
public class TeamsController {
  private static final Logger log = LoggerFactory.getLogger(TeamsController.class);

  private final @NotNull TeamsService teamsService;

  public TeamsController(@NotNull TeamsService teamsService) {
    this.teamsService = teamsService;
  }

  /**
   * Get teams with cursor-based pagination.
   *
   * @param databaseId The database ID
   * @param cursor Optional cursor for pagination (team ID to start from)
   * @param limit Number of teams to return (default 100, max 1000)
   * @return Paginated list of teams
   */
  @GetMapping
  public ResponseEntity<TeamListResponse> getTeams(
      @PathVariable String databaseId,
      @RequestParam(required = false) Integer cursor,
      @RequestParam(defaultValue = "100") int limit) {
    TeamListResponse response = teamsService.getTeams(databaseId, cursor, limit);
    return ResponseEntity.ok(response);
  }

  /**
   * Get a single team by ID.
   *
   * @param databaseId The database ID
   * @param teamId The team ID
   * @return The team information, or 404 if not found
   */
  @GetMapping("/{teamId}")
  public ResponseEntity<TeamDto> getTeam(
      @PathVariable String databaseId, @PathVariable int teamId) {
    try {
      TeamDto team = teamsService.getTeam(databaseId, teamId);
      if (team == null) {
        return ResponseEntity.notFound().build();
      }
      return ResponseEntity.ok(team);
    } catch (MorphyServiceException e) {
      log.error(
          "Error retrieving team {} from database '{}': {}", teamId, databaseId, e.getMessage());
      return ResponseEntity.internalServerError().build();
    }
  }

  /**
   * Get the total count of teams in the database.
   *
   * @param databaseId The database ID
   * @return The count of teams
   */
  @GetMapping("/count")
  public ResponseEntity<TeamCountResponse> getTeamCount(@PathVariable String databaseId) {
    int count = teamsService.getTeamCount(databaseId);
    return ResponseEntity.ok(new TeamCountResponse(count));
  }

  /**
   * Update an existing team in the database.
   *
   * @param databaseId The database ID
   * @param teamId The ID of the team to update
   * @param teamDto The new team data
   * @return The updated team
   */
  @PutMapping("/{teamId}")
  public ResponseEntity<TeamDto> updateTeam(
      @PathVariable String databaseId, @PathVariable int teamId, @RequestBody TeamDto teamDto) {
    try {
      TeamDto updatedTeam = teamsService.updateTeam(databaseId, teamId, teamDto);
      return ResponseEntity.ok(updatedTeam);
    } catch (MorphyServiceException e) {
      log.error(
          "Error updating team {} in database '{}': {}", teamId, databaseId, e.getMessage());
      return ResponseEntity.internalServerError().build();
    } catch (Exception e) {
      log.error("Unexpected error updating team {} in database '{}'", teamId, databaseId, e);
      return ResponseEntity.notFound().build();
    }
  }

  @GetMapping("/search")
  public ResponseEntity<EntitySearchResponse<TeamDto>> searchTeams(
      @PathVariable String databaseId,
      @RequestParam(required = false) String filter,
      @RequestParam(required = false) Integer offset,
      @RequestParam(required = false) Integer limit,
      @RequestParam(required = false) String sortBy,
      @RequestParam(required = false) String order) {
    EntitySearchRequest request = new EntitySearchRequest(filter, offset, limit, sortBy, order);
    EntitySearchResponse<TeamDto> response = teamsService.searchTeams(databaseId, request);
    return ResponseEntity.ok(response);
  }
}

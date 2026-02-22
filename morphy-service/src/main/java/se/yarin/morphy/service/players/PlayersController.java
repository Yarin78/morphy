package se.yarin.morphy.service.players;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import se.yarin.morphy.service.MorphyServiceException;
import se.yarin.morphy.service.players.dto.PlayerDto;
import se.yarin.morphy.service.search.EntitySearchRequest;
import se.yarin.morphy.service.search.EntitySearchResponse;

@RestController
@RequestMapping("/api/databases/{databaseId}/players")
public class PlayersController {
  private static final Logger log = LoggerFactory.getLogger(PlayersController.class);

  private final @NotNull PlayersService playersService;

  public PlayersController(@NotNull PlayersService playersService) {
    this.playersService = playersService;
  }

  /**
   * Get players with cursor-based pagination.
   *
   * @param databaseId The database ID
   * @param cursor Optional cursor for pagination (player ID to start from)
   * @param limit Number of players to return (default 100, max 1000)
   * @return Paginated list of players
   */
  @GetMapping
  public ResponseEntity<PlayerListResponse> getPlayers(
      @PathVariable String databaseId,
      @RequestParam(required = false) Integer cursor,
      @RequestParam(defaultValue = "100") int limit) {
    PlayerListResponse response = playersService.getPlayers(databaseId, cursor, limit);
    return ResponseEntity.ok(response);
  }

  /**
   * Get a single player by ID.
   *
   * @param databaseId The database ID
   * @param playerId The player ID
   * @return The player information, or 404 if not found
   */
  @GetMapping("/{playerId}")
  public ResponseEntity<PlayerDto> getPlayer(
      @PathVariable String databaseId, @PathVariable int playerId) {
    try {
      PlayerDto player = playersService.getPlayer(databaseId, playerId);
      if (player == null) {
        return ResponseEntity.notFound().build();
      }
      return ResponseEntity.ok(player);
    } catch (MorphyServiceException e) {
      log.error(
          "Error retrieving player {} from database '{}': {}",
          playerId,
          databaseId,
          e.getMessage());
      return ResponseEntity.internalServerError().build();
    }
  }

  /**
   * Get the total count of players in the database.
   *
   * @param databaseId The database ID
   * @return The count of players
   */
  @GetMapping("/count")
  public ResponseEntity<PlayerCountResponse> getPlayerCount(@PathVariable String databaseId) {
    int count = playersService.getPlayerCount(databaseId);
    return ResponseEntity.ok(new PlayerCountResponse(count));
  }

  /**
   * Update an existing player in the database.
   *
   * @param databaseId The database ID
   * @param playerId The ID of the player to update
   * @param playerDto The new player data
   * @return The updated player
   */
  @PutMapping("/{playerId}")
  public ResponseEntity<PlayerDto> updatePlayer(
      @PathVariable String databaseId,
      @PathVariable int playerId,
      @RequestBody PlayerDto playerDto) {
    try {
      PlayerDto updatedPlayer = playersService.updatePlayer(databaseId, playerId, playerDto);
      return ResponseEntity.ok(updatedPlayer);
    } catch (MorphyServiceException e) {
      log.error(
          "Error updating player {} in database '{}': {}", playerId, databaseId, e.getMessage());
      return ResponseEntity.internalServerError().build();
    } catch (Exception e) {
      log.error("Unexpected error updating player {} in database '{}'", playerId, databaseId, e);
      return ResponseEntity.notFound().build();
    }
  }

  @GetMapping("/search")
  public ResponseEntity<EntitySearchResponse<PlayerDto>> searchPlayers(
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
    EntitySearchResponse<PlayerDto> response = playersService.searchPlayers(databaseId, request);
    return ResponseEntity.ok(response);
  }
}

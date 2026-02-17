package se.yarin.morphy.service.gametags;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import se.yarin.morphy.service.MorphyServiceException;
import se.yarin.morphy.service.gametags.dto.GameTagDto;
import se.yarin.morphy.service.search.EntitySearchRequest;
import se.yarin.morphy.service.search.EntitySearchResponse;

@RestController
@RequestMapping("/api/databases/{databaseId}/gametags")
public class GameTagsController {
  private static final Logger log = LoggerFactory.getLogger(GameTagsController.class);

  private final @NotNull GameTagsService gameTagsService;

  public GameTagsController(@NotNull GameTagsService gameTagsService) {
    this.gameTagsService = gameTagsService;
  }

  /**
   * Get game tags with cursor-based pagination.
   *
   * @param databaseId The database ID
   * @param cursor Optional cursor for pagination (game tag ID to start from)
   * @param limit Number of game tags to return (default 100, max 1000)
   * @return Paginated list of game tags
   */
  @GetMapping
  public ResponseEntity<GameTagListResponse> getGameTags(
      @PathVariable String databaseId,
      @RequestParam(required = false) Integer cursor,
      @RequestParam(defaultValue = "100") int limit) {
    GameTagListResponse response = gameTagsService.getGameTags(databaseId, cursor, limit);
    return ResponseEntity.ok(response);
  }

  /**
   * Get a single game tag by ID.
   *
   * @param databaseId The database ID
   * @param gameTagId The game tag ID
   * @return The game tag information, or 404 if not found
   */
  @GetMapping("/{gameTagId}")
  public ResponseEntity<GameTagDto> getGameTag(
      @PathVariable String databaseId, @PathVariable int gameTagId) {
    try {
      GameTagDto gameTag = gameTagsService.getGameTag(databaseId, gameTagId);
      if (gameTag == null) {
        return ResponseEntity.notFound().build();
      }
      return ResponseEntity.ok(gameTag);
    } catch (MorphyServiceException e) {
      log.error(
          "Error retrieving game tag {} from database '{}': {}",
          gameTagId,
          databaseId,
          e.getMessage());
      return ResponseEntity.internalServerError().build();
    }
  }

  /**
   * Get the total count of game tags in the database.
   *
   * @param databaseId The database ID
   * @return The count of game tags
   */
  @GetMapping("/count")
  public ResponseEntity<GameTagCountResponse> getGameTagCount(@PathVariable String databaseId) {
    int count = gameTagsService.getGameTagCount(databaseId);
    return ResponseEntity.ok(new GameTagCountResponse(count));
  }

  /**
   * Update an existing game tag in the database.
   *
   * @param databaseId The database ID
   * @param gameTagId The ID of the game tag to update
   * @param gameTagDto The new game tag data
   * @return The updated game tag
   */
  @PutMapping("/{gameTagId}")
  public ResponseEntity<GameTagDto> updateGameTag(
      @PathVariable String databaseId,
      @PathVariable int gameTagId,
      @RequestBody GameTagDto gameTagDto) {
    try {
      GameTagDto updatedGameTag =
          gameTagsService.updateGameTag(databaseId, gameTagId, gameTagDto);
      return ResponseEntity.ok(updatedGameTag);
    } catch (IllegalArgumentException e) {
      log.error("Invalid game tag data: {}", e.getMessage());
      return ResponseEntity.badRequest().build();
    } catch (MorphyServiceException e) {
      log.error(
          "Error updating game tag {} in database '{}': {}",
          gameTagId,
          databaseId,
          e.getMessage());
      return ResponseEntity.internalServerError().build();
    } catch (Exception e) {
      log.error(
          "Unexpected error updating game tag {} in database '{}'", gameTagId, databaseId, e);
      return ResponseEntity.notFound().build();
    }
  }

  @GetMapping("/search")
  public ResponseEntity<EntitySearchResponse<GameTagDto>> searchGameTags(
      @PathVariable String databaseId,
      @RequestParam(required = false) String filter,
      @RequestParam(required = false) Integer offset,
      @RequestParam(required = false) Integer limit,
      @RequestParam(required = false) String sortBy,
      @RequestParam(required = false) String order) {
    try {
      EntitySearchRequest request = new EntitySearchRequest(filter, offset, limit, sortBy, order);
      EntitySearchResponse<GameTagDto> response =
          gameTagsService.searchGameTags(databaseId, request);
      return ResponseEntity.ok(response);
    } catch (IllegalArgumentException e) {
      log.error("Invalid search parameters: {}", e.getMessage());
      return ResponseEntity.badRequest().build();
    } catch (MorphyServiceException e) {
      log.error("Error searching game tags in database '{}': {}", databaseId, e.getMessage());
      return ResponseEntity.internalServerError().build();
    }
  }
}

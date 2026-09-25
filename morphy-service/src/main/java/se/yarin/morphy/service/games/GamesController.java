package se.yarin.morphy.service.games;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import se.yarin.morphy.service.CountResponse;
import se.yarin.morphy.service.MorphyServiceException;
import se.yarin.morphy.service.games.GamesService;
import se.yarin.morphy.model.GameDto;
import se.yarin.morphy.service.games.dto.GameSearchRequest;
import se.yarin.morphy.service.games.dto.GameSearchResponse;

@RestController
@RequestMapping("/api/databases/{databaseId}/games")
public class GamesController {
  private static final Logger log = LoggerFactory.getLogger(GamesController.class);

  private final GamesService gamesService;

  public GamesController(GamesService gamesService) {
    this.gamesService = gamesService;
  }

  /**
   * Lists games in id order.
   *
   * @param databaseId The database ID
   * @param offset Number of games to skip (default 0)
   * @param limit Number of games to return (default 100, max 1000)
   * @param includeMoves Whether to include game moves (default false)
   * @param includeText Whether to include game text/commentary (default false)
   * @return One page of games, in the same form as a search result
   */
  @GetMapping
  public ResponseEntity<GameSearchResponse> getGames(
      @PathVariable String databaseId,
      @RequestParam(defaultValue = "0") int offset,
      @RequestParam(defaultValue = "100") int limit,
      @RequestParam(defaultValue = "false") boolean includeMoves,
      @RequestParam(defaultValue = "false") boolean includeText) {
    GameSearchResponse response =
        gamesService.getGames(databaseId, offset, limit, includeMoves, includeText);
    return ResponseEntity.ok(response);
  }

  /**
   * Get a single game by ID.
   *
   * @param databaseId The database ID
   * @param gameId The game ID
   * @param includeMoves Whether to include game moves (default true)
   * @param includeText Whether to include game text/commentary (default false)
   * @return The game with the requested information, or 404 if not found
   */
  @GetMapping("/{gameId}")
  public ResponseEntity<GameDto> getGame(
      @PathVariable String databaseId,
      @PathVariable long gameId,
      @RequestParam(defaultValue = "true") boolean includeMoves,
      @RequestParam(defaultValue = "false") boolean includeText) {
    try {
      GameDto game = gamesService.getGame(databaseId, gameId, includeMoves, includeText);
      if (game == null) {
        return ResponseEntity.notFound().build();
      }
      return ResponseEntity.ok(game);
    } catch (MorphyServiceException e) {
      log.error(
          "Error retrieving game {} from database '{}': {}", gameId, databaseId, e.getMessage());
      return ResponseEntity.internalServerError().build();
    }
  }

  /**
   * Get the total count of games in the database.
   *
   * @param databaseId The database ID
   * @return The count of games
   */
  @GetMapping("/count")
  public ResponseEntity<CountResponse> getGameCount(@PathVariable String databaseId) {
    return ResponseEntity.ok(new CountResponse(gamesService.getGameCount(databaseId)));
  }

  /**
   * Add a new game to the database.
   *
   * @param databaseId The database ID
   * @param gameDto The game data to add
   * @return The created game with its assigned ID
   */
  @PostMapping
  public ResponseEntity<GameDto> addGame(
      @PathVariable String databaseId, @RequestBody GameDto gameDto) {
    try {
      GameDto createdGame = gamesService.addGame(databaseId, gameDto);
      return ResponseEntity.status(201).body(createdGame);
    } catch (MorphyServiceException e) {
      log.error("Error adding game to database '{}': {}", databaseId, e.getMessage());
      return ResponseEntity.internalServerError().build();
    } catch (Exception e) {
      log.error("Unexpected error adding game to database '{}'", databaseId, e);
      return ResponseEntity.internalServerError().build();
    }
  }

  /**
   * Replace an existing game in the database.
   *
   * @param databaseId The database ID
   * @param gameId The ID of the game to replace
   * @param gameDto The new game data
   * @return The updated game
   */
  @PutMapping("/{gameId}")
  public ResponseEntity<GameDto> replaceGame(
      @PathVariable String databaseId, @PathVariable long gameId, @RequestBody GameDto gameDto) {
    try {
      GameDto updatedGame = gamesService.replaceGame(databaseId, gameId, gameDto);
      return ResponseEntity.ok(updatedGame);
    } catch (MorphyServiceException e) {
      log.error("Error replacing game {} in database '{}': {}", gameId, databaseId, e.getMessage());
      return ResponseEntity.internalServerError().build();
    } catch (Exception e) {
      log.error("Unexpected error replacing game {} in database '{}'", gameId, databaseId, e);
      return ResponseEntity.notFound().build();
    }
  }

  /**
   * Search for games using filters, sorting, and pagination.
   *
   * <p>Supports both simple typed query parameters and a complex filter query language. The query
   * parameters are the fields of {@link GameSearchRequest}.
   *
   * <p>Examples:
   * <ul>
   *   <li>Simple: {@code ?result=1-0&ratingMin=2600&playerId=123}
   *   <li>Complex: {@code ?filter=result:1-0 AND rating:2600.. AND player.name:Carlsen}
   * </ul>
   *
   * @param databaseId The database ID
   * @param request The search request, bound from the query parameters
   * @return Search results with matching games and metadata
   */
  @GetMapping("/search")
  public ResponseEntity<GameSearchResponse> searchGames(
      @PathVariable String databaseId, @ModelAttribute GameSearchRequest request) {
    return ResponseEntity.ok(gamesService.searchGames(databaseId, request));
  }

  /**
   * Search for games using POST with request body.
   *
   * <p>Useful for very long filter strings that might exceed URL length limits.
   *
   * @param databaseId The database ID
   * @param request The search request
   * @return Search results with matching games and metadata
   */
  @PostMapping("/search")
  public ResponseEntity<GameSearchResponse> searchGamesPost(
      @PathVariable String databaseId, @RequestBody GameSearchRequest request) {
    GameSearchResponse response = gamesService.searchGames(databaseId, request);
    return ResponseEntity.ok(response);
  }
}

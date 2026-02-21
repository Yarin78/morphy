package se.yarin.morphy.service.games;

import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import se.yarin.morphy.service.MorphyServiceException;
import se.yarin.morphy.service.games.GameCountResponse;
import se.yarin.morphy.service.games.GameHeaderListResponse;
import se.yarin.morphy.service.games.GamesService;
import se.yarin.morphy.service.games.dto.GameDto;
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
   * Get games with cursor-based pagination.
   *
   * @param databaseId The database ID
   * @param cursor Optional cursor for pagination (game ID to start from)
   * @param limit Number of games to return (default 100, max 1000)
   * @param includeMoves Whether to include game moves (default false)
   * @param includeText Whether to include game text/commentary (default false)
   * @return Paginated list of games
   */
  @GetMapping
  public ResponseEntity<GameHeaderListResponse> getGames(
      @PathVariable String databaseId,
      @RequestParam(required = false) Integer cursor,
      @RequestParam(defaultValue = "100") int limit,
      @RequestParam(defaultValue = "false") boolean includeMoves,
      @RequestParam(defaultValue = "false") boolean includeText) {
    GameHeaderListResponse response =
        gamesService.getGames(databaseId, cursor, limit, includeMoves, includeText);
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
      @PathVariable int gameId,
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
  public ResponseEntity<GameCountResponse> getGameCount(@PathVariable String databaseId) {
    int count = gamesService.getGameCount(databaseId);
    return ResponseEntity.ok(new GameCountResponse(count));
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
      @PathVariable String databaseId, @PathVariable int gameId, @RequestBody GameDto gameDto) {
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
   * <p>Supports both simple typed query parameters and a complex filter query language.
   *
   * <p>Examples:
   * <ul>
   *   <li>Simple: {@code ?result=1-0&ratingMin=2600&playerId=123}
   *   <li>Complex: {@code ?filter=result:1-0 AND rating:2600.. AND player.name:Carlsen}
   * </ul>
   *
   * @param databaseId The database ID
   * @param offset Skip N games (default 0)
   * @param limit Return max N games (default 50, max 1000)
   * @param sortBy Sort spec: field with optional +/- prefix (e.g. "+id", "-date"; default "+id")
   * @param includeMoves Whether to include game moves (default false)
   * @param includeText Whether to include game text/commentary (default false)
   * @param filter Complex filter query string (optional)
   * @param result Game result filter (e.g., "1-0", "0-1", "1/2-1/2")
   * @param dateFrom Filter games from this date (inclusive)
   * @param dateTo Filter games to this date (inclusive)
   * @param ecoCode ECO code filter (supports wildcards like "B9*")
   * @param round Round number filter
   * @param ratingMin Minimum rating filter
   * @param ratingMax Maximum rating filter
   * @param ratingMode Rating mode: "any", "both", "white", "black", "average", "difference"
   * @param playerId Player ID filter
   * @param playerPosition Player position: "white", "black", "any", "both", "winner", "loser"
   * @param tournamentId Tournament ID filter
   * @param annotatorId Annotator ID filter
   * @param sourceId Source ID filter
   * @param teamId Team ID filter
   * @param teamPosition Team position: "white", "black", "any", "winner", "loser"
   * @param gameTagId Game tag ID filter
   * @param debugQueryPlans Include query plan debug info (default false)
   * @param debugExecuteAllPlans Execute all candidate plans for comparison (default false)
   * @return Search results with matching games and metadata
   */
  @GetMapping("/search")
  public ResponseEntity<GameSearchResponse> searchGames(
      @PathVariable String databaseId,
      @RequestParam(required = false) Integer offset,
      @RequestParam(required = false) Integer limit,
      @RequestParam(required = false) String sortBy,
      @RequestParam(required = false) Boolean includeMoves,
      @RequestParam(required = false) Boolean includeText,
      @RequestParam(required = false) String filter,
      @RequestParam(required = false) String result,
      @RequestParam(required = false) LocalDate dateFrom,
      @RequestParam(required = false) LocalDate dateTo,
      @RequestParam(required = false) String ecoCode,
      @RequestParam(required = false) Integer round,
      @RequestParam(required = false) Integer ratingMin,
      @RequestParam(required = false) Integer ratingMax,
      @RequestParam(required = false) String ratingMode,
      @RequestParam(required = false) Integer playerId,
      @RequestParam(required = false) String playerPosition,
      @RequestParam(required = false) Integer tournamentId,
      @RequestParam(required = false) Integer annotatorId,
      @RequestParam(required = false) Integer sourceId,
      @RequestParam(required = false) Integer teamId,
      @RequestParam(required = false) String teamPosition,
      @RequestParam(required = false) Integer gameTagId,
      @RequestParam(required = false) Boolean debugQueryPlans,
      @RequestParam(required = false) Boolean debugExecuteAllPlans) {

    GameSearchRequest request =
        new GameSearchRequest(
            offset,
            limit,
            sortBy,
            includeMoves,
            includeText,
            filter,
            result,
            dateFrom,
            dateTo,
            ecoCode,
            round,
            ratingMin,
            ratingMax,
            ratingMode,
            playerId,
            playerPosition,
            tournamentId,
            annotatorId,
            sourceId,
            teamId,
            teamPosition,
            gameTagId,
            debugQueryPlans,
            debugExecuteAllPlans);

    GameSearchResponse response = gamesService.searchGames(databaseId, request);
    return ResponseEntity.ok(response);
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

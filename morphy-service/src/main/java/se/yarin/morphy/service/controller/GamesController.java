package se.yarin.morphy.service.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import se.yarin.morphy.service.MorphyServiceException;
import se.yarin.morphy.service.games.GameCountResponse;
import se.yarin.morphy.service.games.GameHeaderListResponse;
import se.yarin.morphy.service.games.GamesService;
import se.yarin.morphy.service.games.dto.GameDto;

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
     * @param cursor     Optional cursor for pagination (game ID to start from)
     * @param limit      Number of games to return (default 100, max 1000)
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
     * @param gameId     The game ID
     * @param includeMoves Whether to include game moves (default true)
     * @param includeText Whether to include game text/commentary (default false)
     * @return The game with the requested information
     */
    @GetMapping("/{gameId}")
    public ResponseEntity<GameDto> getGame(
            @PathVariable String databaseId,
            @PathVariable int gameId,
            @RequestParam(defaultValue = "true") boolean includeMoves,
            @RequestParam(defaultValue = "false") boolean includeText) {
        try {
            GameDto game = gamesService.getGame(databaseId, gameId, includeMoves, includeText);
            return ResponseEntity.ok(game);
        } catch (MorphyServiceException e) {
            log.error("Error retrieving game {} from database '{}': {}", gameId, databaseId, e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error retrieving game {} from database '{}'", gameId, databaseId, e);
            return ResponseEntity.notFound().build();
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
     * @param gameDto    The game data to add
     * @return The created game with its assigned ID
     */
    @PostMapping
    public ResponseEntity<GameDto> addGame(
            @PathVariable String databaseId,
            @RequestBody GameDto gameDto) {
        try {
            GameDto createdGame = gamesService.addGame(databaseId, gameDto);
            return ResponseEntity.status(201).body(createdGame);
        } catch (IllegalArgumentException e) {
            log.error("Invalid game data: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
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
     * @param gameId     The ID of the game to replace
     * @param gameDto    The new game data
     * @return The updated game
     */
    @PutMapping("/{gameId}")
    public ResponseEntity<GameDto> replaceGame(
            @PathVariable String databaseId,
            @PathVariable int gameId,
            @RequestBody GameDto gameDto) {
        try {
            GameDto updatedGame = gamesService.replaceGame(databaseId, gameId, gameDto);
            return ResponseEntity.ok(updatedGame);
        } catch (IllegalArgumentException e) {
            log.error("Invalid game data: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (MorphyServiceException e) {
            log.error("Error replacing game {} in database '{}': {}", gameId, databaseId, e.getMessage());
            return ResponseEntity.internalServerError().build();
        } catch (Exception e) {
            log.error("Unexpected error replacing game {} in database '{}'", gameId, databaseId, e);
            return ResponseEntity.notFound().build();
        }
    }
}

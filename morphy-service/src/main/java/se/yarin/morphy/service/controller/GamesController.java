package se.yarin.morphy.service.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import se.yarin.morphy.service.MorphyServiceException;
import se.yarin.morphy.service.games.GameCountResponse;
import se.yarin.morphy.service.games.GameHeaderListResponse;
import se.yarin.morphy.service.games.GameResponse;
import se.yarin.morphy.service.games.GamesService;

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
     * @return Paginated list of game headers
     */
    @GetMapping
    public ResponseEntity<GameHeaderListResponse> getGames(
            @PathVariable String databaseId,
            @RequestParam(required = false) Integer cursor,
            @RequestParam(defaultValue = "100") int limit) {
        GameHeaderListResponse response =
                gamesService.getGameHeadersPaginated(databaseId, cursor, limit);
        return ResponseEntity.ok(response);
    }

    /**
     * Get a single game by ID.
     *
     * @param databaseId The database ID
     * @param gameId     The game ID
     * @param format     Optional format for the moves (default: "pgn")
     * @return The complete game with header and moves in the requested format
     */
    @GetMapping("/{gameId}")
    public ResponseEntity<GameResponse> getGame(
            @PathVariable String databaseId,
            @PathVariable int gameId,
            @RequestParam(defaultValue = "pgn") String format) {
        try {
            GameResponse game = gamesService.getGameInFormat(databaseId, gameId, format);
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
}

package se.yarin.morphy.service.games;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import se.yarin.chess.GameHeaderModel;
import se.yarin.chess.GameModel;
import se.yarin.morphy.Game;
import se.yarin.morphy.games.filters.GameFilter;
import se.yarin.morphy.service.MorphyServiceException;
import se.yarin.morphy.service.databases.DatabaseService;
import se.yarin.morphy.service.games.dto.GameDto;
import se.yarin.morphy.service.games.dto.GameDtoConverter;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class GamesService {
    private static final Logger log = LoggerFactory.getLogger(GamesService.class);

    private final DatabaseService databaseService;
    private final GameDtoConverter gameDtoConverter;

    public GamesService(DatabaseService databaseService, GameDtoConverter gameDtoConverter) {
        this.databaseService = databaseService;
        this.gameDtoConverter = gameDtoConverter;
    }

    /**
     * Get game headers starting from a specific game ID (cursor-based pagination).
     *
     * @param databaseId The database ID to search in
     * @param cursor     The cursor (game ID) to start from, null for beginning
     * @param limit      Maximum number of games to return (max 1000)
     * @param includeMoves Whether to include game moves in the response
     * @param includeText Whether to include game text/commentary in the response
     * @return Paginated response with games and pagination info
     */
    public GameHeaderListResponse getGameHeadersPaginated(
            @NotNull String databaseId, Integer cursor, int limit, boolean includeMoves, boolean includeText) {
        if (limit <= 0) {
            log.warn("Limit must be positive, got: {}", limit);
            return new GameHeaderListResponse(new ArrayList<>(), 0, null, false);
        }
        if (limit > 1000) {
            log.warn("Too high limit provided ({}), reduced to 1000", limit);
            limit = 1000;
        }

        final int finalLimit = limit;
        // Fetch limit+1 to determine if there are more results
        // For list queries, use minimal event/source/team details for better performance
        List<GameDto> allGames =
                databaseService.withReadTransaction(
                        databaseId,
                        txn ->
                                txn.stream(cursor, null)
                                        .filter(morphyGame -> !morphyGame.guidingText())
                                        .limit(finalLimit + 1L)
                                        .map(game -> gameDtoConverter.toDto(game, includeMoves, includeText, false, false, false))
                                        .collect(Collectors.toList()));

        boolean hasMore = allGames.size() > limit;
        List<GameDto> games = hasMore ? allGames.subList(0, limit) : allGames;

        String nextCursor = null;
        if (hasMore && !games.isEmpty()) {
            // The next cursor is the ID of the last game we're returning + 1
            long lastGameId = games.get(games.size() - 1).id();
            nextCursor = String.valueOf(lastGameId + 1);
        }

        return new GameHeaderListResponse(games, games.size(), nextCursor, hasMore);
    }

    /**
     * Get a game as a DTO with optional moves and text.
     *
     * @param databaseId The database ID
     * @param gameId     The game ID
     * @param includeMoves Whether to include game moves in the response
     * @param includeText Whether to include game text/commentary in the response
     * @return GameDto with the requested information (includes full event/source/team details)
     */
    public GameDto getGameDto(@NotNull String databaseId, int gameId, boolean includeMoves, boolean includeText) {
        return databaseService.withReadTransaction(databaseId, txn -> {
            Game game = txn.getGame(gameId);
            // For single game queries, include full event/source/team details
            return gameDtoConverter.toDto(game, includeMoves, includeText, true, true, true);
        });
    }

    public GameModel getGame(@NotNull String databaseId, int gameId) {
        return databaseService.withReadTransaction(databaseId, txn -> {
            Game game = txn.getGame(gameId);
            return game.getModel();
        });
    }

    /**
     * Add a game to the specified database.
     *
     * @param databaseId The database ID to add the game to
     * @param gameModel  The game model to add
     * @return The ID of the newly added game
     * @throws MorphyServiceException if the game cannot be added
     */
    public int addGameToDatabase(@NotNull String databaseId, @NotNull GameModel gameModel) {
        try {
            return databaseService.withWriteTransaction(databaseId, txn -> {
                Game game = txn.addGame(gameModel);
                log.info("Successfully added game {} to database '{}'", game.id(), databaseId);
                return game.id();
            });
        } catch (Exception e) {
            throw new MorphyServiceException("Failed to add game to database '" + databaseId + "'", e);
        }
    }

    /**
     * Replace an existing game in the specified database.
     *
     * @param databaseId The database ID to replace the game in
     * @param gameId     The game ID to replace
     * @param gameModel  The new game model to replace with
     * @return The ID of the replaced game
     * @throws MorphyServiceException if the game cannot be replaced
     */
    public int replaceGameInDatabase(@NotNull String databaseId, int gameId, @NotNull GameModel gameModel) {
        try {
            return databaseService.withWriteTransaction(databaseId, txn -> {
                Game game = txn.replaceGame(gameId, gameModel);
                log.info("Successfully replaced game {} in database '{}'", game.id(), databaseId);
                return game.id();
            });
        } catch (Exception e) {
            throw new MorphyServiceException("Failed to replace game " + gameId + " in database '" + databaseId + "'", e);
        }
    }

    /**
     * Get the total number of games in the specified database.
     *
     * @param databaseId The database ID to count games in
     * @return The total number of games in the database
     */
    public int getGameCount(@NotNull String databaseId) {
        return databaseService.withReadTransaction(databaseId, txn -> txn.database().count());
    }

    /**
     * Add multiple games to the specified database in a single transaction.
     *
     * @param databaseId The database ID to add games to
     * @param gameModels The list of game models to add
     * @return List of game IDs of the added games
     * @throws MorphyServiceException if any game cannot be added
     */
    public List<Integer> addGamesToDatabase(@NotNull String databaseId, @NotNull List<GameModel> gameModels) {
        if (gameModels.isEmpty()) {
            log.warn("No games provided to add");
            return new ArrayList<>();
        }

        try {
            return databaseService.withWriteTransaction(databaseId, txn -> {
                List<Integer> gameIds = new ArrayList<>();
                for (GameModel gameModel : gameModels) {
                    Game game = txn.addGame(gameModel);
                    gameIds.add(game.id());
                }
                log.info("Successfully added {} game(s) to database '{}'", gameIds.size(), databaseId);
                return gameIds;
            });
        } catch (Exception e) {
            throw new MorphyServiceException("Failed to add games to database '" + databaseId + "'", e);
        }
    }
}

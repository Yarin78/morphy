package se.yarin.morphy.service.games;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import se.yarin.chess.GameModel;
import se.yarin.morphy.Game;
import se.yarin.morphy.service.MorphyServiceException;
import se.yarin.morphy.service.databases.DatabaseService;
import se.yarin.morphy.service.games.dto.GameDto;
import se.yarin.morphy.service.games.dto.GameDtoConverter;
import se.yarin.morphy.service.games.dto.GameDtoImporter;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class GamesService {
    private static final Logger log = LoggerFactory.getLogger(GamesService.class);

    private final DatabaseService databaseService;
    private final GameDtoConverter gameDtoConverter;
    private final GameDtoImporter gameDtoImporter;

    public GamesService(DatabaseService databaseService, GameDtoConverter gameDtoConverter, GameDtoImporter gameDtoImporter) {
        this.databaseService = databaseService;
        this.gameDtoConverter = gameDtoConverter;
        this.gameDtoImporter = gameDtoImporter;
    }

    /**
     * Get game headers starting from a specific game ID (cursor-based pagination).
     *
     * @param databaseId   The database ID to search in
     * @param cursor       The cursor (game ID) to start from, null for beginning
     * @param limit        Maximum number of games to return (max 1000)
     * @param includeMoves Whether to include game moves in the response
     * @param includeText  Whether to include game text/commentary in the response
     * @return Paginated response with games and pagination info
     */
    public GameHeaderListResponse getGames(
            @NotNull String databaseId,
            Integer cursor,
            int limit,
            boolean includeMoves,
            boolean includeText) {
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
     * @param databaseId   The database ID
     * @param gameId       The game ID
     * @param includeMoves Whether to include game moves in the response
     * @param includeText  Whether to include game text/commentary in the response
     * @return GameDto with the requested information (includes full event/source/team details)
     */
    public GameDto getGame(@NotNull String databaseId, int gameId, boolean includeMoves, boolean includeText) {
        return databaseService.withReadTransaction(databaseId, txn -> {
            Game game = txn.getGame(gameId);
            // For single game queries, include full event/source/team details
            return gameDtoConverter.toDto(game, includeMoves, includeText, true, true, true);
        });
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
     * Add a game from a DTO to the specified database.
     *
     * @param databaseId The database ID to add the game to
     * @param gameDto    The game DTO to add
     * @return The complete GameDto of the added game (includes full event/source/team details)
     * @throws MorphyServiceException   if the game cannot be added
     * @throws IllegalArgumentException if the DTO is invalid or represents guiding text
     */
    public GameDto addGame(@NotNull String databaseId, @NotNull GameDto gameDto) {
        try {
            GameModel gameModel = gameDtoImporter.toGameModel(gameDto);
            int gameId = databaseService.withWriteTransaction(databaseId, txn -> {
                Game game = txn.addGame(gameModel);
                log.info("Successfully added game {} to database '{}'", game.id(), databaseId);
                return game.id();
            });
            return getGame(databaseId, gameId, true, true);
        } catch (IllegalArgumentException e) {
            throw e;  // Rethrow validation errors
        } catch (MorphyServiceException e) {
            throw e;  // Rethrow service errors
        } catch (Exception e) {
            throw new MorphyServiceException("Failed to add game to database '" + databaseId + "'", e);
        }
    }

    /**
     * Replace a game from a DTO in the specified database.
     *
     * @param databaseId The database ID to replace the game in
     * @param gameId     The game ID to replace
     * @param gameDto    The game DTO to replace with
     * @return The complete GameDto of the replaced game (includes full event/source/team details)
     * @throws MorphyServiceException   if the game cannot be replaced
     * @throws IllegalArgumentException if the DTO is invalid or represents guiding text
     */
    public GameDto replaceGame(@NotNull String databaseId, int gameId, @NotNull GameDto gameDto) {
        try {
            GameModel gameModel = gameDtoImporter.toGameModel(gameDto);
            databaseService.withWriteTransaction(databaseId, txn -> {
                Game game = txn.replaceGame(gameId, gameModel);
                log.info("Successfully replaced game {} in database '{}'", game.id(), databaseId);
                return game.id();
            });
            return getGame(databaseId, gameId, true, true);
        } catch (IllegalArgumentException e) {
            throw e;  // Rethrow validation errors
        } catch (MorphyServiceException e) {
            throw e;  // Rethrow service errors
        } catch (Exception e) {
            throw new MorphyServiceException("Failed to replace game " + gameId + " in database '" + databaseId + "'", e);
        }
    }
}

package se.yarin.morphy.service.games;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import se.yarin.chess.GameHeaderModel;
import se.yarin.chess.GameModel;
import se.yarin.chess.pgn.PgnExporter;
import se.yarin.morphy.Game;
import se.yarin.morphy.games.filters.GameFilter;
import se.yarin.morphy.service.MorphyServiceException;
import se.yarin.morphy.service.databases.DatabaseService;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class GamesService {
    private static final Logger log = LoggerFactory.getLogger(GamesService.class);

    private final DatabaseService databaseService;

    public GamesService(DatabaseService databaseService) {
        this.databaseService = databaseService;
    }

    /**
     * Get game headers starting from a specific game ID (cursor-based pagination).
     *
     * @param databaseId The database ID to search in
     * @param cursor     The cursor (game ID) to start from, null for beginning
     * @param limit      Maximum number of games to return (max 1000)
     * @return Paginated response with games and pagination info
     */
    public GameHeaderListResponse getGameHeadersPaginated(@NotNull String databaseId, Integer cursor, int limit) {
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
        List<GameHeaderResponse> allGames =
                databaseService.withReadTransaction(
                        databaseId,
                        txn ->
                                txn.stream(cursor, null)
                                        .filter(morphyGame -> !morphyGame.guidingText())
                                        .limit(finalLimit + 1L)
                                        .map(game -> new GameHeaderResponse(game.id(), game.getGameHeaderModel()))
                                        .collect(Collectors.toList()));

        boolean hasMore = allGames.size() > limit;
        List<GameHeaderResponse> games = hasMore ? allGames.subList(0, limit) : allGames;

        String nextCursor = null;
        if (hasMore && !games.isEmpty()) {
            // The next cursor is the ID of the last game we're returning + 1
            int lastGameId = games.get(games.size() - 1).gameId();
            nextCursor = String.valueOf(lastGameId + 1);
        }

        return new GameHeaderListResponse(games, games.size(), nextCursor, hasMore);
    }

    public List<GameHeaderModel> getGameHeaders(@NotNull String databaseId, int firstGameId, int limit) {
        return getGameHeaders(databaseId, firstGameId, null, null, limit);
    }

    /**
     * Get games matching the specified filter.
     *
     * @param databaseId The database ID to search in
     * @param filter     The game filter to apply (null returns all games)
     * @param limit      Maximum number of games to return (max 1000)
     * @return List of game headers matching the filter
     */
    public List<GameHeaderModel> getGameHeaders(@NotNull String databaseId, GameFilter filter, int limit) {
        return getGameHeaders(databaseId, null, null, filter, limit);
    }

    /**
     * Get games with optional filtering and range selection.
     *
     * @param databaseId The database ID to search in
     * @param startId    The first game ID (inclusive), null for start of database
     * @param endId      The last game ID (exclusive), null for end of database
     * @param filter     The game filter to apply, null returns all games
     * @param limit      Maximum number of games to return (max 1000)
     * @return List of game headers matching the criteria
     */
    public List<GameHeaderModel> getGameHeaders(@NotNull String databaseId, Integer startId, Integer endId, GameFilter filter, int limit) {
        if (limit <= 0) {
            log.warn("Limit must be positive, got: {}", limit);
            return new ArrayList<>();
        }
        if (limit > 1000) {
            log.warn("Too high limit provided ({}), reduced to 1000", limit);
            limit = 1000;
        }

        final int finalLimit = limit;
        return databaseService.withReadTransaction(databaseId, txn -> txn.stream(startId, endId, filter)
                .filter(morphyGame -> !morphyGame.guidingText())
                .limit(finalLimit)
                .map(Game::getGameHeaderModel)
                .collect(Collectors.toList()));
    }

    /**
     * Get a game with moves in the specified format.
     *
     * @param databaseId The database ID
     * @param gameId     The game ID
     * @param format     The format for the moves (currently only "pgn" is supported)
     * @return GameResponse with header and moves in the requested format
     * @throws MorphyServiceException if the game cannot be retrieved or format is unsupported
     */
    public GameResponse getGameInFormat(@NotNull String databaseId, int gameId, @NotNull String format) {
        if (!"pgn".equalsIgnoreCase(format)) {
            throw new MorphyServiceException("Unsupported format: " + format + ". Only 'pgn' is currently supported.");
        }

        return databaseService.withReadTransaction(databaseId, txn -> {
            Game game = txn.getGame(gameId);
            GameModel model = game.getModel();

            PgnExporter exporter = new PgnExporter();
            String movesPgn = exporter.exportMovesOnly(model.moves());

            return new GameResponse(gameId, model.header(), movesPgn, "pgn");
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

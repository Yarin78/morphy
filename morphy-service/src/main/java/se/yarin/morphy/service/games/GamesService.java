package se.yarin.morphy.service.games;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import se.yarin.chess.GameModel;
import se.yarin.morphy.Game;
import se.yarin.morphy.queries.GameQuery;
import se.yarin.morphy.queries.QueryContext;
import se.yarin.morphy.queries.operations.QueryData;
import se.yarin.morphy.queries.operations.QueryOperator;
import se.yarin.morphy.service.MorphyServiceException;
import se.yarin.morphy.service.databases.DatabaseService;
import se.yarin.morphy.service.games.dto.*;
import se.yarin.morphy.service.games.search.GameSearchRequestConverter;

@Service
public class GamesService {
  private static final Logger log = LoggerFactory.getLogger(GamesService.class);

  private final DatabaseService databaseService;
  private final GameDtoConverter gameDtoConverter;
  private final GameDtoImporter gameDtoImporter;
  private final GameSearchRequestConverter gameQueryBuilder;

  public GamesService(
      DatabaseService databaseService,
      GameDtoConverter gameDtoConverter,
      GameDtoImporter gameDtoImporter,
      GameSearchRequestConverter gameQueryBuilder) {
    this.databaseService = databaseService;
    this.gameDtoConverter = gameDtoConverter;
    this.gameDtoImporter = gameDtoImporter;
    this.gameQueryBuilder = gameQueryBuilder;
  }

  /**
   * Get game headers starting from a specific game ID (cursor-based pagination).
   *
   * @param databaseId The database ID to search in
   * @param cursor The cursor (game ID) to start from, null for beginning
   * @param limit Maximum number of games to return (max 1000)
   * @param includeMoves Whether to include game moves in the response
   * @param includeText Whether to include game text/commentary in the response
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
    // For list queries, use minimal tournament/source/team details for better performance
    List<GameDto> allGames =
        databaseService.withReadTransaction(
            databaseId,
            txn ->
                txn.stream(cursor, null)
                    .filter(morphyGame -> !morphyGame.guidingText())
                    .limit(finalLimit + 1L)
                    .map(
                        game ->
                            gameDtoConverter.toDto(
                                game, includeMoves, includeText, false, false, false))
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
   * @param gameId The game ID
   * @param includeMoves Whether to include game moves in the response
   * @param includeText Whether to include game text/commentary in the response
   * @return GameDto with the requested information (includes full tournament/source/team details),
   *     or null if the game doesn't exist or is deleted
   */
  public @Nullable GameDto getGame(
      @NotNull String databaseId, int gameId, boolean includeMoves, boolean includeText) {
    return databaseService.withReadTransaction(
        databaseId,
        txn -> {
          try {
            Game game = txn.getGame(gameId);
            // For single game queries, include full tournament/source/team details
            return gameDtoConverter.toDto(game, includeMoves, includeText, true, true, true);
          } catch (IllegalArgumentException e) {
            // Game doesn't exist or is deleted
            return null;
          }
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
   * @param gameDto The game DTO to add
   * @return The complete GameDto of the added game (includes full tournament/source/team details)
   * @throws MorphyServiceException if the game cannot be added
   * @throws IllegalArgumentException if the DTO is invalid or represents guiding text
   */
  public GameDto addGame(@NotNull String databaseId, @NotNull GameDto gameDto) {
    try {
      GameModel gameModel = gameDtoImporter.toGameModel(gameDto);
      int gameId =
          databaseService.withWriteTransaction(
              databaseId,
              txn -> {
                Game game = txn.addGame(gameModel);
                log.info("Successfully added game {} to database '{}'", game.id(), databaseId);
                return game.id();
              });
      return getGame(databaseId, gameId, true, true);
    } catch (IllegalArgumentException e) {
      throw e; // Rethrow validation errors
    } catch (MorphyServiceException e) {
      throw e; // Rethrow service errors
    } catch (Exception e) {
      throw new MorphyServiceException("Failed to add game to database '" + databaseId + "'", e);
    }
  }

  /**
   * Replace a game from a DTO in the specified database.
   *
   * @param databaseId The database ID to replace the game in
   * @param gameId The game ID to replace
   * @param gameDto The game DTO to replace with
   * @return The complete GameDto of the replaced game (includes full tournament/source/team
   *     details)
   * @throws MorphyServiceException if the game cannot be replaced
   * @throws IllegalArgumentException if the DTO is invalid or represents guiding text
   */
  public GameDto replaceGame(@NotNull String databaseId, int gameId, @NotNull GameDto gameDto) {
    try {
      GameModel gameModel = gameDtoImporter.toGameModel(gameDto);
      databaseService.withWriteTransaction(
          databaseId,
          txn -> {
            Game game = txn.replaceGame(gameId, gameModel);
            log.info("Successfully replaced game {} in database '{}'", game.id(), databaseId);
            return game.id();
          });
      return getGame(databaseId, gameId, true, true);
    } catch (IllegalArgumentException e) {
      throw e; // Rethrow validation errors
    } catch (MorphyServiceException e) {
      throw e; // Rethrow service errors
    } catch (Exception e) {
      throw new MorphyServiceException(
          "Failed to replace game " + gameId + " in database '" + databaseId + "'", e);
    }
  }

  /**
   * Searches for games matching the given criteria.
   *
   * @param databaseId The database ID to search in
   * @param request The search request with filter criteria, sorting, and pagination
   * @return Search response with matching games and metadata
   */
  public GameSearchResponse searchGames(
      @NotNull String databaseId, @NotNull GameSearchRequest request) {
    long startTime = System.currentTimeMillis();

    // Validate and apply limits
    int offset = Math.max(0, request.offset());
    int limit = Math.min(1000, Math.max(1, request.limit()));

    return databaseService.withReadTransaction(
        databaseId,
        txn -> {
          // 1. Build GameQuery from request
          GameQuery gameQuery = gameQueryBuilder.buildQuery(txn.database(), request);

          // 2. Create query context
          QueryContext context = new QueryContext(txn, false);

          // 3. Generate query plans using existing QueryPlanner
          List<QueryOperator<Game>> plans =
              txn.database().queryPlanner().getGameQueryPlans(context, gameQuery, true);

          // 4. Select best plan
          QueryOperator<Game> bestPlan = txn.database().queryPlanner().selectBestQueryPlan(plans);

          log.debug(
              "Selected query plan: {} (cost: {})",
              bestPlan.getClass().getSimpleName(),
              bestPlan.getQueryCost().estimatedTotalCost());

          // 5. Execute query - get QueryData<Game> stream
          Stream<QueryData<Game>> queryResults = bestPlan.stream();

          // 6. Extract Game objects and filter out guiding text
          List<Game> games =
              queryResults
                  .map(QueryData::data)
                  .filter(game -> !game.guidingText())
                  .collect(Collectors.toList());

          // 7. Apply sorting (if needed - ID order is already sorted, date is handled by QuerySortOrder)
          if (needsSorting(request)) {
            games = sortGames(games, request.sortBy(), request.order());
          }

          // 8. Apply pagination
          int totalCount = games.size();
          int fromIndex = Math.min(offset, totalCount);
          int toIndex = Math.min(offset + limit, totalCount);
          List<Game> paginatedGames = games.subList(fromIndex, toIndex);

          // 9. Convert to DTOs
          List<GameDto> gameDtos =
              paginatedGames.stream()
                  .map(
                      game ->
                          gameDtoConverter.toDto(
                              game,
                              request.includeMoves(),
                              request.includeText(),
                              false,
                              false,
                              false))
                  .collect(Collectors.toList());

          long endTime = System.currentTimeMillis();

          // 10. Build metadata
          SearchMetadata metadata =
              new SearchMetadata(
                  gameQuery.toString(),
                  request.sortBy(),
                  request.order(),
                  endTime - startTime);

          return new GameSearchResponse(
              gameDtos, gameDtos.size(), totalCount, offset, limit, metadata);
        });
  }

  /**
   * Checks if sorting is needed post-query. ID and date sorting are handled by QuerySortOrder, but
   * rating-based sorting must be done here.
   */
  private boolean needsSorting(@NotNull GameSearchRequest request) {
    String sortBy = request.sortBy().toLowerCase();
    return sortBy.equals("whiteelo") || sortBy.equals("blackelo") || sortBy.equals("avgelo");
  }

  /**
   * Sorts games by the specified field and order.
   *
   * @param games the games to sort
   * @param sortBy the field to sort by (whiteElo, blackElo, avgElo)
   * @param order the sort order (asc or desc)
   * @return sorted list of games
   */
  private @NotNull List<Game> sortGames(
      @NotNull List<Game> games, @NotNull String sortBy, @NotNull String order) {
    Comparator<Game> comparator =
        switch (sortBy.toLowerCase()) {
          case "whiteelo" -> Comparator.comparingInt(Game::whiteElo);
          case "blackelo" -> Comparator.comparingInt(Game::blackElo);
          case "avgelo" ->
              Comparator.comparingDouble(game -> (game.whiteElo() + game.blackElo()) / 2.0);
          default ->
              throw new IllegalArgumentException("Cannot sort by field: " + sortBy);
        };

    if ("desc".equalsIgnoreCase(order)) {
      comparator = comparator.reversed();
    }

    return games.stream().sorted(comparator).collect(Collectors.toList());
  }
}

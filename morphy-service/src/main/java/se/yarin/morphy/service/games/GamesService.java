package se.yarin.morphy.service.games;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import se.yarin.morphy.api.Database;
import se.yarin.morphy.api.GameFetchOptions;
import se.yarin.morphy.api.query.Query;
import se.yarin.morphy.api.query.ResultPage;
import se.yarin.morphy.model.GameDto;
import se.yarin.morphy.service.MorphyServiceException;
import se.yarin.morphy.service.databases.DatabaseService;
import se.yarin.morphy.service.games.dto.GameSearchRequest;
import se.yarin.morphy.service.games.dto.GameSearchResponse;
import se.yarin.morphy.service.games.search.GameSearchRequestConverter;
import se.yarin.morphy.service.search.SearchMetadata;

@Service
public class GamesService {
  private static final Logger log = LoggerFactory.getLogger(GamesService.class);

  private final DatabaseService databaseService;
  private final GameSearchRequestConverter searchRequestConverter;

  public GamesService(
      DatabaseService databaseService, GameSearchRequestConverter searchRequestConverter) {
    this.databaseService = databaseService;
    this.searchRequestConverter = searchRequestConverter;
  }

  /**
   * Lists the games of a database in id order.
   *
   * @param databaseId the database ID
   * @param offset the number of games to skip
   * @param limit the maximum number of games to return (at most 1000)
   * @param includeMoves whether to include the moves
   * @param includeText whether to include the body of guiding texts
   */
  public GameSearchResponse getGames(
      @NotNull String databaseId,
      int offset,
      int limit,
      boolean includeMoves,
      boolean includeText) {
    GameSearchRequest request =
        new GameSearchRequest(
            offset, limit, null, includeMoves, includeText, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null, null, null);
    return searchGames(databaseId, request);
  }

  /**
   * Gets a game, with full tournament, source and team details.
   *
   * @return the game, or null if there is no game with that id
   */
  public @Nullable GameDto getGame(
      @NotNull String databaseId, long gameId, boolean includeMoves, boolean includeText) {
    GameFetchOptions fetch = new GameFetchOptions(includeMoves, includeText, true);
    return databaseService.read(databaseId, db -> db.getGame(gameId, fetch));
  }

  /** The number of games in a database. */
  public long getGameCount(@NotNull String databaseId) {
    return databaseService.read(databaseId, Database::gameCount);
  }

  /**
   * Adds a game.
   *
   * @return the added game, as stored
   * @throws IllegalArgumentException if the game is invalid
   * @throws MorphyServiceException if the game can't be added
   */
  public GameDto addGame(@NotNull String databaseId, @NotNull GameDto gameDto) {
    try {
      long gameId = databaseService.write(databaseId, db -> db.addGame(gameDto));
      log.info("Successfully added game {} to database '{}'", gameId, databaseId);
      return getGame(databaseId, gameId, true, true);
    } catch (IllegalArgumentException | MorphyServiceException e) {
      throw e;
    } catch (Exception e) {
      throw new MorphyServiceException("Failed to add game to database '" + databaseId + "'", e);
    }
  }

  /**
   * Replaces a game.
   *
   * @return the replaced game, as stored
   * @throws IllegalArgumentException if the game is invalid or doesn't exist
   * @throws MorphyServiceException if the game can't be replaced
   */
  public GameDto replaceGame(@NotNull String databaseId, long gameId, @NotNull GameDto gameDto) {
    try {
      databaseService.write(
          databaseId,
          db -> {
            db.replaceGame(gameId, gameDto);
            return null;
          });
      log.info("Successfully replaced game {} in database '{}'", gameId, databaseId);
      return getGame(databaseId, gameId, true, true);
    } catch (IllegalArgumentException | MorphyServiceException e) {
      throw e;
    } catch (Exception e) {
      throw new MorphyServiceException(
          "Failed to replace game " + gameId + " in database '" + databaseId + "'", e);
    }
  }

  /**
   * Searches for games.
   *
   * @param databaseId the database ID
   * @param request the filter, typed parameters, sort order and window
   * @return one page of matching games, with metadata
   */
  public GameSearchResponse searchGames(
      @NotNull String databaseId, @NotNull GameSearchRequest request) {
    return databaseService.read(databaseId, db -> search(db, request));
  }

  /** Searches for games in an open database; see {@link #searchGames}. */
  public GameSearchResponse search(@NotNull Database db, @NotNull GameSearchRequest request) {
    long startTime = System.currentTimeMillis();
    Query query = searchRequestConverter.toQuery(request);
    GameFetchOptions fetch =
        new GameFetchOptions(request.includeMoves(), request.includeText(), false);
    ResultPage<GameDto> page = db.findGames(query, fetch);
    SearchMetadata metadata =
        new SearchMetadata(
            page.appliedFilter(), query.sort().toString(), System.currentTimeMillis() - startTime);
    return new GameSearchResponse(
        page.items(),
        page.items().size(),
        page.total() == null ? null : page.total().intValue(),
        page.offset(),
        page.limit(),
        metadata);
  }
}

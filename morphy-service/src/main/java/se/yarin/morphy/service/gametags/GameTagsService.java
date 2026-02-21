package se.yarin.morphy.service.gametags;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.entities.GameTag;
import se.yarin.morphy.queries.filter.GameTagQueryBuilder;
import se.yarin.morphy.service.MorphyServiceException;
import se.yarin.morphy.service.databases.DatabaseService;
import se.yarin.morphy.service.gametags.dto.GameTagDto;
import se.yarin.morphy.service.gametags.dto.GameTagDtoConverter;
import se.yarin.morphy.service.search.EntitySearchExecutor;
import se.yarin.morphy.service.search.EntitySearchRequest;
import se.yarin.morphy.service.search.EntitySearchResponse;

@Service
public class GameTagsService {
  private static final Logger log = LoggerFactory.getLogger(GameTagsService.class);

  private final DatabaseService databaseService;
  private final GameTagDtoConverter gameTagDtoConverter;
  private final EntitySearchExecutor entitySearchExecutor;

  public GameTagsService(
      DatabaseService databaseService,
      GameTagDtoConverter gameTagDtoConverter,
      EntitySearchExecutor entitySearchExecutor) {
    this.databaseService = databaseService;
    this.gameTagDtoConverter = gameTagDtoConverter;
    this.entitySearchExecutor = entitySearchExecutor;
  }

  /**
   * Get game tags with cursor-based pagination.
   *
   * @param databaseId The database ID to search in
   * @param cursor The cursor (game tag ID) to start from, null for beginning
   * @param limit Maximum number of game tags to return (max 1000)
   * @return Paginated response with game tags and pagination info
   */
  public GameTagListResponse getGameTags(@NotNull String databaseId, Integer cursor, int limit) {
    if (limit <= 0) {
      log.warn("Limit must be positive, got: {}", limit);
      return new GameTagListResponse(new ArrayList<>(), 0, null, false);
    }
    if (limit > 1000) {
      log.warn("Too high limit provided ({}), reduced to 1000", limit);
      limit = 1000;
    }

    int startId = (cursor != null) ? cursor : 0;
    final int finalLimit = limit;

    List<GameTagDto> allGameTags =
        databaseService.withReadTransaction(
            databaseId,
            txn -> {
              // Stream game tags from the specified start ID
              return txn.gameTagTransaction().stream(startId, null)
                  .limit(finalLimit + 1L)
                  .map(gameTagDtoConverter::toDto)
                  .collect(Collectors.toList());
            });

    boolean hasMore = allGameTags.size() > limit;
    List<GameTagDto> gameTags = hasMore ? allGameTags.subList(0, limit) : allGameTags;

    String nextCursor = null;
    if (hasMore && !gameTags.isEmpty()) {
      long lastGameTagId = gameTags.get(gameTags.size() - 1).id();
      nextCursor = String.valueOf(lastGameTagId + 1);
    }

    return new GameTagListResponse(gameTags, gameTags.size(), nextCursor, hasMore);
  }

  /**
   * Get a single game tag by ID.
   *
   * @param databaseId The database ID
   * @param gameTagId The game tag ID
   * @return GameTagDto with the game tag information, or null if the game tag doesn't exist, is
   *     deleted, or is unused (has no games)
   */
  public @Nullable GameTagDto getGameTag(@NotNull String databaseId, int gameTagId) {
    return databaseService.withReadTransaction(
        databaseId,
        txn -> {
          try {
            GameTag gameTag = txn.gameTagTransaction().get(gameTagId);
            // Return null if game tag is unused (has no games)
            if (gameTag.count() == 0) {
              return null;
            }
            return gameTagDtoConverter.toDto(gameTag);
          } catch (IllegalArgumentException e) {
            // Game tag doesn't exist or is deleted
            return null;
          }
        });
  }

  /**
   * Get the total number of game tags in the specified database.
   *
   * @param databaseId The database ID to count game tags in
   * @return The total number of game tags in the database
   */
  public int getGameTagCount(@NotNull String databaseId) {
    return databaseService.withReadTransaction(
        databaseId, txn -> txn.database().gameTagIndex().count());
  }

  /**
   * Update an existing game tag in the database.
   *
   * @param databaseId The database ID
   * @param gameTagId The game tag ID to update
   * @param gameTagDto The game tag data to update with
   * @return The updated GameTagDto
   * @throws MorphyServiceException if the game tag cannot be updated
   * @throws IllegalArgumentException if the DTO is invalid
   */
  public GameTagDto updateGameTag(
      @NotNull String databaseId, int gameTagId, @NotNull GameTagDto gameTagDto) {
    try {
      // Convert DTO to entity
      GameTag gameTag = gameTagDtoConverter.toGameTag(gameTagDto);

      // Update in write transaction using the built-in method
      // (automatically preserves count and firstGameId statistics)
      databaseService.withWriteTransaction(
          databaseId,
          txn -> {
            // Check if another game tag with the same key exists
            GameTag existing = txn.gameTagTransaction().get(gameTag);
            if (existing != null && existing.id() != gameTagId) {
              throw new IllegalArgumentException(
                  "Cannot update game tag: another game tag with the same key already exists");
            }

            txn.updateGameTagById(gameTagId, gameTag);
            log.info("Successfully updated game tag {} in database '{}'", gameTagId, databaseId);
          });

      // Return the updated game tag
      return getGameTag(databaseId, gameTagId);
    } catch (IllegalArgumentException e) {
      throw e; // Rethrow validation errors
    } catch (MorphyServiceException e) {
      throw e; // Rethrow service errors
    } catch (Exception e) {
      throw new MorphyServiceException(
          "Failed to update game tag " + gameTagId + " in database '" + databaseId + "'", e);
    }
  }

  public EntitySearchResponse<GameTagDto> searchGameTags(
      @NotNull String databaseId, @NotNull EntitySearchRequest request) {
    GameTagQueryBuilder queryBuilder = new GameTagQueryBuilder();
    return databaseService.withReadTransaction(
        databaseId,
        txn ->
            entitySearchExecutor.executeSearch(
                txn,
                EntityType.GAME_TAG,
                queryBuilder,
                gameTagDtoConverter::toDto,
                request));
  }
}

package se.yarin.morphy.service.players;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.entities.Player;
import se.yarin.morphy.queries.filter.PlayerQueryBuilder;
import se.yarin.morphy.service.MorphyServiceException;
import se.yarin.morphy.service.databases.DatabaseService;
import se.yarin.morphy.service.players.dto.PlayerDto;
import se.yarin.morphy.service.players.dto.PlayerDtoConverter;
import se.yarin.morphy.service.search.EntitySearchExecutor;
import se.yarin.morphy.service.search.EntitySearchRequest;
import se.yarin.morphy.service.search.EntitySearchResponse;

@Service
public class PlayersService {
  private static final Logger log = LoggerFactory.getLogger(PlayersService.class);

  private final DatabaseService databaseService;
  private final PlayerDtoConverter playerDtoConverter;
  private final EntitySearchExecutor entitySearchExecutor;

  public PlayersService(
      DatabaseService databaseService,
      PlayerDtoConverter playerDtoConverter,
      EntitySearchExecutor entitySearchExecutor) {
    this.databaseService = databaseService;
    this.playerDtoConverter = playerDtoConverter;
    this.entitySearchExecutor = entitySearchExecutor;
  }

  /**
   * Get players with cursor-based pagination.
   *
   * @param databaseId The database ID to search in
   * @param cursor The cursor (player ID) to start from, null for beginning
   * @param limit Maximum number of players to return (max 1000)
   * @return Paginated response with players and pagination info
   */
  public PlayerListResponse getPlayers(@NotNull String databaseId, Integer cursor, int limit) {
    if (limit <= 0) {
      log.warn("Limit must be positive, got: {}", limit);
      return new PlayerListResponse(new ArrayList<>(), 0, null, false);
    }
    if (limit > 1000) {
      log.warn("Too high limit provided ({}), reduced to 1000", limit);
      limit = 1000;
    }

    int startId = (cursor != null) ? cursor : 0;
    final int finalLimit = limit;

    List<PlayerDto> allPlayers =
        databaseService.withReadTransaction(
            databaseId,
            txn -> {
              // Stream players from the specified start ID
              return txn.playerTransaction().stream(startId, null)
                  .limit(finalLimit + 1L)
                  .map(playerDtoConverter::toDto)
                  .collect(Collectors.toList());
            });

    boolean hasMore = allPlayers.size() > limit;
    List<PlayerDto> players = hasMore ? allPlayers.subList(0, limit) : allPlayers;

    String nextCursor = null;
    if (hasMore && !players.isEmpty()) {
      long lastPlayerId = players.get(players.size() - 1).id();
      nextCursor = String.valueOf(lastPlayerId + 1);
    }

    return new PlayerListResponse(players, players.size(), nextCursor, hasMore);
  }

  /**
   * Get a single player by ID.
   *
   * @param databaseId The database ID
   * @param playerId The player ID
   * @return PlayerDto with the player information, or null if the player doesn't exist, is
   *     deleted, or is unused (has no games)
   */
  public @Nullable PlayerDto getPlayer(@NotNull String databaseId, int playerId) {
    return databaseService.withReadTransaction(
        databaseId,
        txn -> {
          try {
            Player player = txn.playerTransaction().get(playerId);
            // Return null if player is unused (has no games)
            if (player.count() == 0) {
              return null;
            }
            return playerDtoConverter.toDto(player);
          } catch (IllegalArgumentException e) {
            // Player doesn't exist or is deleted
            return null;
          }
        });
  }

  /**
   * Get the total number of players in the specified database.
   *
   * @param databaseId The database ID to count players in
   * @return The total number of players in the database
   */
  public int getPlayerCount(@NotNull String databaseId) {
    return databaseService.withReadTransaction(
        databaseId, txn -> txn.database().playerIndex().count());
  }

  /**
   * Update an existing player in the database.
   *
   * @param databaseId The database ID
   * @param playerId The player ID to update
   * @param playerDto The player data to update with
   * @return The updated PlayerDto
   * @throws MorphyServiceException if the player cannot be updated
   * @throws IllegalArgumentException if the DTO is invalid
   */
  public PlayerDto updatePlayer(
      @NotNull String databaseId, int playerId, @NotNull PlayerDto playerDto) {
    try {
      // Convert DTO to entity
      Player player = playerDtoConverter.toPlayer(playerDto);

      // Update in write transaction using the built-in method
      // (automatically preserves count and firstGameId statistics)
      databaseService.withWriteTransaction(
          databaseId,
          txn -> {
            // Check if another player with the same key exists
            Player existing = txn.playerTransaction().get(player);
            if (existing != null && existing.id() != playerId) {
              throw new IllegalArgumentException(
                  "Cannot update player: another player with the same key already exists");
            }

            txn.updatePlayerById(playerId, player);
            log.info("Successfully updated player {} in database '{}'", playerId, databaseId);
          });

      // Return the updated player
      return getPlayer(databaseId, playerId);
    } catch (IllegalArgumentException e) {
      throw e; // Rethrow validation errors
    } catch (MorphyServiceException e) {
      throw e; // Rethrow service errors
    } catch (Exception e) {
      throw new MorphyServiceException(
          "Failed to update player " + playerId + " in database '" + databaseId + "'", e);
    }
  }

  public EntitySearchResponse<PlayerDto> searchPlayers(
      @NotNull String databaseId, @NotNull EntitySearchRequest request) {
    PlayerQueryBuilder queryBuilder = new PlayerQueryBuilder();
    boolean debugRawData = request.debugRawData();
    return databaseService.withReadTransaction(
        databaseId,
        txn -> {
          Function<Player, PlayerDto> toDtoFn =
              debugRawData
                  ? p -> playerDtoConverter.toDto(
                      p, txn.database().playerIndex().getRaw(p.id()))
                  : playerDtoConverter::toDto;
          return entitySearchExecutor.executeSearch(
              txn, EntityType.PLAYER, queryBuilder, toDtoFn, request);
        });
  }
}

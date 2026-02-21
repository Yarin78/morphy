package se.yarin.morphy.service.tournaments;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.entities.Tournament;
import se.yarin.morphy.entities.TournamentExtra;
import se.yarin.morphy.queries.filter.TournamentQueryBuilder;
import se.yarin.morphy.service.MorphyServiceException;
import se.yarin.morphy.service.databases.DatabaseService;
import se.yarin.morphy.service.search.EntitySearchExecutor;
import se.yarin.morphy.service.search.EntitySearchRequest;
import se.yarin.morphy.service.search.EntitySearchResponse;
import se.yarin.morphy.service.tournaments.dto.TournamentDto;
import se.yarin.morphy.service.tournaments.dto.TournamentDtoConverter;

@Service
public class TournamentsService {
  private static final Logger log = LoggerFactory.getLogger(TournamentsService.class);

  private final DatabaseService databaseService;
  private final TournamentDtoConverter tournamentDtoConverter;
  private final EntitySearchExecutor entitySearchExecutor;

  public TournamentsService(
      DatabaseService databaseService,
      TournamentDtoConverter tournamentDtoConverter,
      EntitySearchExecutor entitySearchExecutor) {
    this.databaseService = databaseService;
    this.tournamentDtoConverter = tournamentDtoConverter;
    this.entitySearchExecutor = entitySearchExecutor;
  }

  /**
   * Get tournaments with cursor-based pagination.
   *
   * @param databaseId The database ID to search in
   * @param cursor The cursor (tournament ID) to start from, null for beginning
   * @param limit Maximum number of tournaments to return (max 1000)
   * @return Paginated response with tournaments and pagination info
   */
  public TournamentListResponse getTournaments(
      @NotNull String databaseId, Integer cursor, int limit) {
    if (limit <= 0) {
      log.warn("Limit must be positive, got: {}", limit);
      return new TournamentListResponse(new ArrayList<>(), 0, null, false);
    }
    if (limit > 1000) {
      log.warn("Too high limit provided ({}), reduced to 1000", limit);
      limit = 1000;
    }

    int startId = (cursor != null) ? cursor : 0;
    final int finalLimit = limit;

    List<TournamentDto> allTournaments =
        databaseService.withReadTransaction(
            databaseId,
            txn -> {
              // Stream tournaments from the specified start ID
              return txn.tournamentTransaction().stream(startId, null)
                  .limit(finalLimit + 1L)
                  .map(
                      tournament -> {
                        TournamentExtra extra = txn.getTournamentExtra(tournament.id());
                        return tournamentDtoConverter.toDto(tournament, extra);
                      })
                  .collect(Collectors.toList());
            });

    boolean hasMore = allTournaments.size() > limit;
    List<TournamentDto> tournaments = hasMore ? allTournaments.subList(0, limit) : allTournaments;

    String nextCursor = null;
    if (hasMore && !tournaments.isEmpty()) {
      long lastTournamentId = tournaments.get(tournaments.size() - 1).id();
      nextCursor = String.valueOf(lastTournamentId + 1);
    }

    return new TournamentListResponse(tournaments, tournaments.size(), nextCursor, hasMore);
  }

  /**
   * Get a single tournament by ID.
   *
   * @param databaseId The database ID
   * @param tournamentId The tournament ID
   * @return TournamentDto with the tournament information, or null if the tournament doesn't exist,
   *     is deleted, or is unused (has no games)
   */
  public @Nullable TournamentDto getTournament(@NotNull String databaseId, int tournamentId) {
    return databaseService.withReadTransaction(
        databaseId,
        txn -> {
          try {
            Tournament tournament = txn.getTournament(tournamentId);
            TournamentExtra extra = txn.getTournamentExtra(tournamentId);
            // Return null if tournament is unused (has no games)
            if (tournament.count() == 0) {
              return null;
            }
            return tournamentDtoConverter.toDto(tournament, extra);
          } catch (IllegalArgumentException e) {
            // Tournament doesn't exist or is deleted
            return null;
          }
        });
  }

  /**
   * Get the total number of tournaments in the specified database.
   *
   * @param databaseId The database ID to count tournaments in
   * @return The total number of tournaments in the database
   */
  public int getTournamentCount(@NotNull String databaseId) {
    return databaseService.withReadTransaction(
        databaseId, txn -> txn.database().tournamentIndex().count());
  }

  /**
   * Update an existing tournament in the database.
   *
   * @param databaseId The database ID
   * @param tournamentId The tournament ID to update
   * @param tournamentDto The tournament data to update with
   * @return The updated TournamentDto
   * @throws MorphyServiceException if the tournament cannot be updated
   * @throws IllegalArgumentException if the DTO is invalid
   */
  public TournamentDto updateTournament(
      @NotNull String databaseId, int tournamentId, @NotNull TournamentDto tournamentDto) {
    try {
      // Convert DTO to entities
      Tournament tournament = tournamentDtoConverter.toTournament(tournamentDto);
      TournamentExtra extra = tournamentDtoConverter.toTournamentExtra(tournamentDto);

      // Update in write transaction using the built-in method
      // (automatically preserves count and firstGameId statistics)
      databaseService.withWriteTransaction(
          databaseId,
          txn -> {
            // Check if another tournament with the same key exists
            Tournament existing = txn.tournamentTransaction().get(tournament);
            if (existing != null && existing.id() != tournamentId) {
              throw new IllegalArgumentException(
                  "Cannot update tournament: another tournament with the same key already exists");
            }

            txn.updateTournamentById(tournamentId, tournament, extra);
            log.info(
                "Successfully updated tournament {} in database '{}'", tournamentId, databaseId);
          });

      // Return the updated tournament
      return getTournament(databaseId, tournamentId);
    } catch (IllegalArgumentException e) {
      throw e; // Rethrow validation errors
    } catch (MorphyServiceException e) {
      throw e; // Rethrow service errors
    } catch (Exception e) {
      throw new MorphyServiceException(
          "Failed to update tournament " + tournamentId + " in database '" + databaseId + "'", e);
    }
  }

  /**
   * Searches for tournaments matching the given criteria.
   *
   * @param databaseId The database ID to search in
   * @param request The search request with filter criteria, sorting, and pagination
   * @return Search response with matching tournaments and metadata
   */
  public EntitySearchResponse<TournamentDto> searchTournaments(
      @NotNull String databaseId, @NotNull EntitySearchRequest request) {
    TournamentQueryBuilder queryBuilder = new TournamentQueryBuilder();
    return databaseService.withReadTransaction(
        databaseId,
        txn ->
            entitySearchExecutor.executeSearch(
                txn,
                EntityType.TOURNAMENT,
                queryBuilder,
                tournament -> {
                  TournamentExtra extra = txn.getTournamentExtra(tournament.id());
                  return tournamentDtoConverter.toDto(tournament, extra);
                },
                request));
  }
}

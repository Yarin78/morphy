package se.yarin.morphy.service.teams;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import se.yarin.morphy.entities.Team;
import se.yarin.morphy.service.MorphyServiceException;
import se.yarin.morphy.service.databases.DatabaseService;
import se.yarin.morphy.service.teams.dto.TeamDto;
import se.yarin.morphy.service.teams.dto.TeamDtoConverter;

@Service
public class TeamsService {
  private static final Logger log = LoggerFactory.getLogger(TeamsService.class);

  private final DatabaseService databaseService;
  private final TeamDtoConverter teamDtoConverter;

  public TeamsService(DatabaseService databaseService, TeamDtoConverter teamDtoConverter) {
    this.databaseService = databaseService;
    this.teamDtoConverter = teamDtoConverter;
  }

  /**
   * Get teams with cursor-based pagination.
   *
   * @param databaseId The database ID to search in
   * @param cursor The cursor (team ID) to start from, null for beginning
   * @param limit Maximum number of teams to return (max 1000)
   * @return Paginated response with teams and pagination info
   */
  public TeamListResponse getTeams(@NotNull String databaseId, Integer cursor, int limit) {
    if (limit <= 0) {
      log.warn("Limit must be positive, got: {}", limit);
      return new TeamListResponse(new ArrayList<>(), 0, null, false);
    }
    if (limit > 1000) {
      log.warn("Too high limit provided ({}), reduced to 1000", limit);
      limit = 1000;
    }

    int startId = (cursor != null) ? cursor : 0;
    final int finalLimit = limit;

    List<TeamDto> allTeams =
        databaseService.withReadTransaction(
            databaseId,
            txn -> {
              // Stream teams from the specified start ID
              return txn.teamTransaction().stream(startId, null)
                  .limit(finalLimit + 1L)
                  .map(teamDtoConverter::toDto)
                  .collect(Collectors.toList());
            });

    boolean hasMore = allTeams.size() > limit;
    List<TeamDto> teams = hasMore ? allTeams.subList(0, limit) : allTeams;

    String nextCursor = null;
    if (hasMore && !teams.isEmpty()) {
      long lastTeamId = teams.get(teams.size() - 1).id();
      nextCursor = String.valueOf(lastTeamId + 1);
    }

    return new TeamListResponse(teams, teams.size(), nextCursor, hasMore);
  }

  /**
   * Get a single team by ID.
   *
   * @param databaseId The database ID
   * @param teamId The team ID
   * @return TeamDto with the team information, or null if the team doesn't exist, is deleted, or
   *     is unused (has no games)
   */
  public @Nullable TeamDto getTeam(@NotNull String databaseId, int teamId) {
    return databaseService.withReadTransaction(
        databaseId,
        txn -> {
          try {
            Team team = txn.teamTransaction().get(teamId);
            // Return null if team is unused (has no games)
            if (team.count() == 0) {
              return null;
            }
            return teamDtoConverter.toDto(team);
          } catch (IllegalArgumentException e) {
            // Team doesn't exist or is deleted
            return null;
          }
        });
  }

  /**
   * Get the total number of teams in the specified database.
   *
   * @param databaseId The database ID to count teams in
   * @return The total number of teams in the database
   */
  public int getTeamCount(@NotNull String databaseId) {
    return databaseService.withReadTransaction(
        databaseId, txn -> txn.database().teamIndex().count());
  }

  /**
   * Update an existing team in the database.
   *
   * @param databaseId The database ID
   * @param teamId The team ID to update
   * @param teamDto The team data to update with
   * @return The updated TeamDto
   * @throws MorphyServiceException if the team cannot be updated
   * @throws IllegalArgumentException if the DTO is invalid
   */
  public TeamDto updateTeam(@NotNull String databaseId, int teamId, @NotNull TeamDto teamDto) {
    try {
      // Convert DTO to entity
      Team team = teamDtoConverter.toTeam(teamDto);

      // Update in write transaction using the built-in method
      // (automatically preserves count and firstGameId statistics)
      databaseService.withWriteTransaction(
          databaseId,
          txn -> {
            // Check if another team with the same key exists
            Team existing = txn.teamTransaction().get(team);
            if (existing != null && existing.id() != teamId) {
              throw new IllegalArgumentException(
                  "Cannot update team: another team with the same key already exists");
            }

            txn.updateTeamById(teamId, team);
            log.info("Successfully updated team {} in database '{}'", teamId, databaseId);
          });

      // Return the updated team
      return getTeam(databaseId, teamId);
    } catch (IllegalArgumentException e) {
      throw e; // Rethrow validation errors
    } catch (MorphyServiceException e) {
      throw e; // Rethrow service errors
    } catch (Exception e) {
      throw new MorphyServiceException(
          "Failed to update team " + teamId + " in database '" + databaseId + "'", e);
    }
  }
}

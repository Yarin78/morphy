package se.yarin.morphy.service.teams.dto;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;
import se.yarin.morphy.entities.ImmutableTeam;
import se.yarin.morphy.entities.Nation;
import se.yarin.morphy.entities.Team;

/** Converter for bidirectional transformation between Team entities and TeamDto objects. */
@Component
public class TeamDtoConverter {

  /**
   * Converts a Team entity to a TeamDto.
   *
   * @param team the team entity to convert
   * @return the TeamDto
   */
  public TeamDto toDto(@NotNull Team team) {
    return toDto(team, null);
  }

  public TeamDto toDto(@NotNull Team team, @Nullable byte[] rawData) {
    return new TeamDto(
        (long) team.id(),
        team.title().isEmpty() ? null : team.title(),
        team.teamNumber() == 0 ? null : team.teamNumber(),
        team.season() ? true : null,
        team.year() == 0 ? null : team.year(),
        team.nation() != Nation.NONE ? team.nation().getIocCode() : null,
        team.count() > 0 ? team.count() : null,
        rawData);
  }

  /**
   * Converts a TeamDto to a Team entity.
   *
   * @param dto the team DTO to convert
   * @return a Team entity
   */
  public Team toTeam(@NotNull TeamDto dto) {
    ImmutableTeam.Builder builder = ImmutableTeam.builder();

    // Set title
    if (dto.title() != null) {
      builder.title(dto.title());
    }

    // Set team number
    if (dto.teamNumber() != null) {
      builder.teamNumber(dto.teamNumber());
    }

    // Set season
    if (dto.season() != null && dto.season()) {
      builder.season(true);
    }

    // Set year
    if (dto.year() != null) {
      builder.year(dto.year());
    }

    // Set nation
    if (dto.nation() != null) {
      Nation nation = Nation.fromIOC(dto.nation());
      if (nation != null) {
        builder.nation(nation);
      }
    }

    return builder.build();
  }
}

package se.yarin.morphy.service.tournaments.dto;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;
import se.yarin.chess.Date;
import se.yarin.morphy.entities.*;

/**
 * Converter for bidirectional transformation between Tournament entities and TournamentDto objects.
 */
@Component
public class TournamentDtoConverter {

  /**
   * Converts a Tournament entity to a TournamentDto.
   *
   * @param tournament the tournament entity to convert
   * @param extra the tournament extra data (contains endDate and other extended information)
   * @return the TournamentDto
   */
  public TournamentDto toDto(@NotNull Tournament tournament, @NotNull TournamentExtra extra) {
    return toDto(tournament, extra, null, null);
  }

  public TournamentDto toDto(
      @NotNull Tournament tournament,
      @NotNull TournamentExtra extra,
      @Nullable byte[] rawData,
      @Nullable byte[] rawExtraData) {
    return new TournamentDto(
        (long) tournament.id(),
        tournament.title(),
        tournament.date().isUnset() ? null : tournament.date(),
        extra.endDate().isUnset() ? null : extra.endDate(),
        tournament.place().isEmpty() ? null : tournament.place(),
        tournament.nation() != Nation.NONE ? tournament.nation().getIocCode() : null,
        tournament.category() == 0 ? null : tournament.category(),
        tournament.rounds() == 0 ? null : tournament.rounds(),
        tournament.type() == TournamentType.NONE ? null : tournament.type().getName(),
        tournament.timeControl() == TournamentTimeControl.NORMAL
            ? null
            : tournament.timeControl().getName(),
        tournament.complete() ? true : null,
        tournament.teamTournament() ? true : null,
        tournament.count() > 0 ? tournament.count() : null,
        rawData,
        rawExtraData);
  }

  /**
   * Converts a TournamentDto to a Tournament entity.
   *
   * @param dto the tournament DTO to convert
   * @return a Tournament entity
   */
  public Tournament toTournament(@NotNull TournamentDto dto) {
    ImmutableTournament.Builder builder = ImmutableTournament.builder();

    // Set title (required)
    builder.title(dto.title() != null ? dto.title() : "");

    // Set dates
    if (dto.startDate() != null) {
      builder.date(dto.startDate());
    } else {
      builder.date(Date.unset());
    }

    // Set location
    if (dto.place() != null) {
      builder.place(dto.place());
    }

    // Set country/nation
    if (dto.country() != null) {
      Nation nation = Nation.fromIOC(dto.country());
      if (nation != null) {
        builder.nation(nation);
      }
    }

    // Set tournament details
    if (dto.category() != null) {
      builder.category(dto.category());
    }

    if (dto.rounds() != null) {
      builder.rounds(dto.rounds());
    }

    if (dto.type() != null) {
      TournamentType type = TournamentType.fromName(dto.type());
      builder.type(type);
    }

    if (dto.timeControl() != null) {
      TournamentTimeControl timeControl = TournamentTimeControl.fromName(dto.timeControl());
      builder.timeControl(timeControl);
    }

    // Set flags
    if (dto.complete() != null && dto.complete()) {
      builder.complete(true);
    }

    if (dto.teamTournament() != null && dto.teamTournament()) {
      builder.teamTournament(true);
    }

    return builder.build();
  }

  /**
   * Converts a TournamentDto to a TournamentExtra entity.
   *
   * @param dto the tournament DTO to convert
   * @return a TournamentExtra entity
   */
  public TournamentExtra toTournamentExtra(@NotNull TournamentDto dto) {
    ImmutableTournamentExtra.Builder builder = ImmutableTournamentExtra.builder();

    // Set end date if available
    if (dto.endDate() != null) {
      builder.endDate(dto.endDate());
    } else {
      builder.endDate(Date.unset());
    }

    return builder.build();
  }
}

package se.yarin.morphy.service.players.dto;

import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import se.yarin.morphy.entities.ImmutablePlayer;
import se.yarin.morphy.entities.Player;

/** Converter for bidirectional transformation between Player entities and PlayerDto objects. */
@Component
public class PlayerDtoConverter {

  /**
   * Converts a Player entity to a PlayerDto.
   *
   * @param player the player entity to convert
   * @return the PlayerDto
   */
  public PlayerDto toDto(@NotNull Player player) {
    return new PlayerDto(
        (long) player.id(),
        player.lastName().isEmpty() ? null : player.lastName(),
        player.firstName().isEmpty() ? null : player.firstName(),
        player.count() > 0 ? player.count() : null);
  }

  /**
   * Converts a PlayerDto to a Player entity.
   *
   * @param dto the player DTO to convert
   * @return a Player entity
   */
  public Player toPlayer(@NotNull PlayerDto dto) {
    ImmutablePlayer.Builder builder = ImmutablePlayer.builder();

    // Set last name
    if (dto.lastName() != null) {
      builder.lastName(dto.lastName());
    }

    // Set first name
    if (dto.firstName() != null) {
      builder.firstName(dto.firstName());
    }

    return builder.build();
  }
}

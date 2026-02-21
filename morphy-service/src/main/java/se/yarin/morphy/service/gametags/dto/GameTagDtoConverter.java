package se.yarin.morphy.service.gametags.dto;

import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import se.yarin.morphy.entities.GameTag;
import se.yarin.morphy.entities.ImmutableGameTag;

/** Converter for bidirectional transformation between GameTag entities and GameTagDto objects. */
@Component
public class GameTagDtoConverter {

  /**
   * Converts a GameTag entity to a GameTagDto.
   *
   * @param gameTag the game tag entity to convert
   * @return the GameTagDto
   */
  public GameTagDto toDto(@NotNull GameTag gameTag) {
    return new GameTagDto(
        (long) gameTag.id(),
        gameTag.title().isEmpty() ? null : gameTag.title(),
        gameTag.languages().isEmpty() ? null : gameTag.languages(),
        gameTag.languageCount(),
        gameTag.englishTitle().isEmpty() ? null : gameTag.englishTitle(),
        gameTag.germanTitle().isEmpty() ? null : gameTag.germanTitle(),
        gameTag.frenchTitle().isEmpty() ? null : gameTag.frenchTitle(),
        gameTag.spanishTitle().isEmpty() ? null : gameTag.spanishTitle(),
        gameTag.italianTitle().isEmpty() ? null : gameTag.italianTitle(),
        gameTag.dutchTitle().isEmpty() ? null : gameTag.dutchTitle(),
        gameTag.slovenianTitle().isEmpty() ? null : gameTag.slovenianTitle(),
        gameTag.resTitle().isEmpty() ? null : gameTag.resTitle(),
        gameTag.count() > 0 ? gameTag.count() : null);
  }

  /**
   * Converts a GameTagDto to a GameTag entity.
   *
   * @param dto the game tag DTO to convert
   * @return a GameTag entity
   */
  public GameTag toGameTag(@NotNull GameTagDto dto) {
    ImmutableGameTag.Builder builder = ImmutableGameTag.builder();

    // Set English title
    if (dto.englishTitle() != null) {
      builder.englishTitle(dto.englishTitle());
    }

    // Set German title
    if (dto.germanTitle() != null) {
      builder.germanTitle(dto.germanTitle());
    }

    // Set French title
    if (dto.frenchTitle() != null) {
      builder.frenchTitle(dto.frenchTitle());
    }

    // Set Spanish title
    if (dto.spanishTitle() != null) {
      builder.spanishTitle(dto.spanishTitle());
    }

    // Set Italian title
    if (dto.italianTitle() != null) {
      builder.italianTitle(dto.italianTitle());
    }

    // Set Dutch title
    if (dto.dutchTitle() != null) {
      builder.dutchTitle(dto.dutchTitle());
    }

    // Set Slovenian title
    if (dto.slovenianTitle() != null) {
      builder.slovenianTitle(dto.slovenianTitle());
    }

    // Set reserved title
    if (dto.resTitle() != null) {
      builder.resTitle(dto.resTitle());
    }

    return builder.build();
  }
}

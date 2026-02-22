package se.yarin.morphy.service.sources.dto;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;
import se.yarin.chess.Date;
import se.yarin.morphy.entities.ImmutableSource;
import se.yarin.morphy.entities.Source;
import se.yarin.morphy.entities.SourceQuality;

/** Converter for bidirectional transformation between Source entities and SourceDto objects. */
@Component
public class SourceDtoConverter {

  /**
   * Converts a Source entity to a SourceDto.
   *
   * @param source the source entity to convert
   * @return the SourceDto
   */
  public SourceDto toDto(@NotNull Source source) {
    return toDto(source, null);
  }

  public SourceDto toDto(@NotNull Source source, @Nullable byte[] rawData) {
    return new SourceDto(
        (long) source.id(),
        source.title().isEmpty() ? null : source.title(),
        source.publisher().isEmpty() ? null : source.publisher(),
        source.publication().isUnset() ? null : source.publication(),
        source.date().isUnset() ? null : source.date(),
        source.version() == 0 ? null : source.version(),
        source.quality() == SourceQuality.UNSET ? null : source.quality().name(),
        source.count() > 0 ? source.count() : null,
        rawData);
  }

  /**
   * Converts a SourceDto to a Source entity.
   *
   * @param dto the source DTO to convert
   * @return a Source entity
   */
  public Source toSource(@NotNull SourceDto dto) {
    ImmutableSource.Builder builder = ImmutableSource.builder();

    // Set title
    if (dto.title() != null) {
      builder.title(dto.title());
    }

    // Set publisher
    if (dto.publisher() != null) {
      builder.publisher(dto.publisher());
    }

    // Set publication date
    if (dto.publication() != null) {
      builder.publication(dto.publication());
    } else {
      builder.publication(Date.unset());
    }

    // Set date
    if (dto.date() != null) {
      builder.date(dto.date());
    } else {
      builder.date(Date.unset());
    }

    // Set version
    if (dto.version() != null) {
      builder.version(dto.version());
    }

    // Set quality
    if (dto.quality() != null) {
      try {
        SourceQuality quality = SourceQuality.valueOf(dto.quality());
        builder.quality(quality);
      } catch (IllegalArgumentException e) {
        // Invalid quality value, use default
        builder.quality(SourceQuality.UNSET);
      }
    }

    return builder.build();
  }
}

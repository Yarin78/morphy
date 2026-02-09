package se.yarin.morphy.service.annotators.dto;

import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import se.yarin.morphy.entities.Annotator;
import se.yarin.morphy.entities.ImmutableAnnotator;

/**
 * Converter for bidirectional transformation between Annotator entities and AnnotatorDto objects.
 */
@Component
public class AnnotatorDtoConverter {

  /**
   * Converts an Annotator entity to an AnnotatorDto.
   *
   * @param annotator the annotator entity to convert
   * @return the AnnotatorDto
   */
  public AnnotatorDto toDto(@NotNull Annotator annotator) {
    return new AnnotatorDto(
        (long) annotator.id(),
        annotator.name().isEmpty() ? null : annotator.name(),
        annotator.count() > 0 ? annotator.count() : null);
  }

  /**
   * Converts an AnnotatorDto to an Annotator entity.
   *
   * @param dto the annotator DTO to convert
   * @return an Annotator entity
   */
  public Annotator toAnnotator(@NotNull AnnotatorDto dto) {
    ImmutableAnnotator.Builder builder = ImmutableAnnotator.builder();

    // Set name
    if (dto.name() != null) {
      builder.name(dto.name());
    }

    return builder.build();
  }
}

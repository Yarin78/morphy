package se.yarin.morphy.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.chess.GameTagLanguage;

/**
 * Detailed information about a game tag.
 *
 * <p>Game tags can have titles in multiple languages. All language fields are optional and only
 * included when set. Each format offers its own languages: v1 has Slovenian, v2 has Portuguese.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
  "id",
  "title",
  "languages",
  "languageCount",
  "englishTitle",
  "germanTitle",
  "frenchTitle",
  "spanishTitle",
  "italianTitle",
  "dutchTitle",
  "slovenianTitle",
  "portugueseTitle",
  "resTitle",
  "gameCount"
})
public record GameTagDto(
    Long id,
    @Nullable String title,
    @Nullable String languages,
    @Nullable Integer languageCount,
    @Nullable String englishTitle,
    @Nullable String germanTitle,
    @Nullable String frenchTitle,
    @Nullable String spanishTitle,
    @Nullable String italianTitle,
    @Nullable String dutchTitle,
    @Nullable String slovenianTitle,
    @Nullable String portugueseTitle,
    @Nullable String resTitle,
    @Nullable Integer gameCount) implements EntityDto {

  /** The title in a language, null if there is none. */
  public @Nullable String title(@NotNull GameTagLanguage language) {
    return switch (language) {
      case ENGLISH -> englishTitle;
      case GERMAN -> germanTitle;
      case FRENCH -> frenchTitle;
      case SPANISH -> spanishTitle;
      case ITALIAN -> italianTitle;
      case DUTCH -> dutchTitle;
      case SLOVENIAN -> slovenianTitle;
      case PORTUGUESE -> portugueseTitle;
    };
  }
}

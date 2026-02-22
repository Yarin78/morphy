package se.yarin.morphy.service.gametags.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.jetbrains.annotations.Nullable;

/**
 * Detailed information about a game tag.
 *
 * <p>Game tags can have titles in multiple languages. All language fields are optional and only
 * included when set.
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
  "resTitle",
  "gameCount",
  "rawData"
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
    @Nullable String resTitle,
    @Nullable Integer gameCount,
    @Nullable byte[] rawData) {}

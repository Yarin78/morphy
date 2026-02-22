package se.yarin.morphy.service.sources.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.jetbrains.annotations.Nullable;
import se.yarin.chess.Date;

/** Detailed information about a chess game source (book, database, etc.). */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
  "id",
  "title",
  "publisher",
  "publication",
  "date",
  "version",
  "quality",
  "gameCount",
  "rawData"
})
public record SourceDto(
    Long id,
    @Nullable String title,
    @Nullable String publisher,
    @Nullable Date publication,
    @Nullable Date date,
    @Nullable Integer version,
    @Nullable String quality,
    @Nullable Integer gameCount,
    @Nullable byte[] rawData) {}

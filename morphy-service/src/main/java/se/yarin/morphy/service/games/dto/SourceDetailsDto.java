package se.yarin.morphy.service.games.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.jetbrains.annotations.Nullable;
import se.yarin.chess.Date;

/** Information about the source of a game (book, database, etc.). */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"id", "name", "title", "date"})
public record SourceDetailsDto(
    Long id, @Nullable String name, @Nullable String title, @Nullable Date date) {}

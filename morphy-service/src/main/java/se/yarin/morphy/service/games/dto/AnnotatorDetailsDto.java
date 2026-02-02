package se.yarin.morphy.service.games.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.jetbrains.annotations.Nullable;

/** Information about the game annotator. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"id", "name"})
public record AnnotatorDetailsDto(Long id, @Nullable String name) {}

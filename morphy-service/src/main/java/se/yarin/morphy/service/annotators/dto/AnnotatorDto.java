package se.yarin.morphy.service.annotators.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.jetbrains.annotations.Nullable;

/** Detailed information about a game annotator. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"id", "name", "gameCount", "rawData"})
public record AnnotatorDto(
    Long id, @Nullable String name, @Nullable Integer gameCount, @Nullable byte[] rawData) {}

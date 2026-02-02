package se.yarin.morphy.service.games.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.jetbrains.annotations.Nullable;

/**
 * Information about a game tag.
 *
 * <p>Game tags can have titles in multiple languages. Currently only English is exposed, but more
 * languages can be added in the future.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"id", "title"})
public record GameTagDetailsDto(Long id, @Nullable String title) {}

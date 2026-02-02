package se.yarin.morphy.service.games.dto;

import org.jetbrains.annotations.Nullable;

/**
 * Text commentary associated with a game.
 *
 * <p>The text header information (event, annotator, source, etc.) is already represented in the
 * main GameDTO, so this DTO only contains the actual text content.
 */
public record GameTextDto(@Nullable String contents) {}

package se.yarin.morphy.cli.columns;

import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.model.GameDto;

/**
 * One row to render, as seen by a {@link GameColumn}.
 *
 * @param dto the neutral game data (never null)
 * @param databaseName the name of the database the row came from, or null if unknown
 */
public record GameRow(GameDto dto, @Nullable String databaseName) {}

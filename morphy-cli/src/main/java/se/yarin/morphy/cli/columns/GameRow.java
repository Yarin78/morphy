package se.yarin.morphy.cli.columns;

import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.Game;
import se.yarin.morphy.model.GameDto;

/**
 * One row to render, as seen by a {@link GameColumn}.
 *
 * <p>The vendor-neutral {@link GameDto} is the primary source for the common columns. Some
 * v1-specific columns (rating type, raw record bytes, tournament statistics that need the database)
 * have no neutral counterpart yet and read the underlying v1 {@link Game} instead; that is {@code
 * null} for rows not backed by a v1 database, and such columns render blank.
 *
 * @param dto the neutral game data (never null)
 * @param game the backing v1 game, or null if the source is not a v1 database
 * @param databaseName the name of the database the row came from, or null if unknown
 */
public record GameRow(GameDto dto, @Nullable Game game, @Nullable String databaseName) {}

package se.yarin.morphy.cli.columns;

import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.model.TournamentDto;

/**
 * One row to render, as seen by a {@link TournamentColumn}.
 *
 * @param dto the neutral tournament data (never null)
 * @param databaseName the name of the database the row came from, or null if unknown
 */
public record TournamentRow(TournamentDto dto, @Nullable String databaseName) {}

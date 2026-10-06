package se.yarin.morphy.service.positions;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A player who played a move.
 *
 * @param name "Lastname, Firstname"
 * @param rating their highest rating in the games they played it in
 */
public record PositionPlayer(@NotNull String name, @Nullable Integer rating) {}

package se.yarin.morphy.positions;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.chess.MoveCode;

/**
 * The games that played one move from a position.
 *
 * @param moveCode the move, see {@link MoveCode}; {@link IndexFiles#GAME_ENDED} for
 *     the games that ended in the position
 * @param gameIds the games, in id order
 * @param stats their statistics, if the index stores them
 */
record MoveGroup(
    int moveCode, int @NotNull [] gameIds, @Nullable MoveStats stats) {}

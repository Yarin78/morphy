package se.yarin.morphy.service.positions;

import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * The games that reached a position and the moves they played from it.
 *
 * @param fen the position, as asked for
 * @param games the games, including those that ended in the position
 * @param whiteWins the games White won
 * @param draws the games drawn
 * @param blackWins the games Black won
 * @param recentSince the first year of the games each move's {@code recentGames} counts
 * @param moves the moves, the most played first
 */
public record PositionSummary(
    @NotNull String fen,
    int games,
    int whiteWins,
    int draws,
    int blackWins,
    int recentSince,
    @NotNull List<PositionMove> moves) {}

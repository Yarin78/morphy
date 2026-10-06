package se.yarin.morphy.service.positions;

import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A move played from a position, and how its games went.
 *
 * @param san the move
 * @param games the games that played it
 * @param whiteWins the games White won
 * @param draws the games drawn
 * @param blackWins the games Black won
 * @param recentGames the games since the summary's {@code recentSince} year
 * @param lastPlayed the year of the latest game, if any game has a year
 * @param averageRating the average rating of the players who played it, of those rated
 * @param topPlayers some of the highest rated players who played it, the highest first
 */
public record PositionMove(
    @NotNull String san,
    int games,
    int whiteWins,
    int draws,
    int blackWins,
    int recentGames,
    @Nullable Integer lastPlayed,
    @Nullable Integer averageRating,
    @NotNull List<PositionPlayer> topPlayers) {}

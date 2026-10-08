package se.yarin.morphy.api;

import org.jetbrains.annotations.NotNull;
import se.yarin.chess.Date;
import se.yarin.chess.GameResult;

/**
 * The header facts of a game a position index keeps: what the statistics of a position's moves
 * and the sorting of its games need.
 *
 * @param result the result
 * @param date the date played, parts 0 when unknown
 * @param whiteElo White's rating, 0 if none
 * @param blackElo Black's rating, 0 if none
 * @param whitePlayerId White's player id in the database, -1 if none
 * @param blackPlayerId Black's player id in the database, -1 if none
 */
public record GameFacts(
    @NotNull GameResult result,
    @NotNull Date date,
    int whiteElo,
    int blackElo,
    long whitePlayerId,
    long blackPlayerId) {}

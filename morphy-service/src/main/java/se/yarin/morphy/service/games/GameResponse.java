package se.yarin.morphy.service.games;

import se.yarin.chess.GameHeaderModel;

/**
 * Response containing a complete game.
 *
 * @param gameId The game ID in the database
 * @param header The game header with metadata (players, event, result, etc.)
 * @param moves The game moves in PGN format (moves only, without headers)
 * @param movesFormat The format of the moves field (e.g., "pgn")
 */
public record GameResponse(
    int gameId, GameHeaderModel header, String moves, String movesFormat) {}

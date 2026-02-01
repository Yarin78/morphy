package se.yarin.morphy.service.games;

import se.yarin.chess.GameHeaderModel;

/**
 * Response wrapper that includes the game ID along with the header model. The game ID is needed
 * for cursor-based pagination and for fetching the full game.
 */
public record GameHeaderResponse(int gameId, GameHeaderModel header) {}

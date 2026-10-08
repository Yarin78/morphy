package se.yarin.morphy.positions;

/**
 * A player who played a move, with their highest rating in the games they played it in.
 *
 * @param playerId the player's id in the database
 * @param elo the rating
 */
public record RatedPlayer(long playerId, int elo) {}

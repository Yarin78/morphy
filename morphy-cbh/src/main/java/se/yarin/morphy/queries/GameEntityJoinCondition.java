package se.yarin.morphy.queries;

public enum GameEntityJoinCondition {
    // Default
    ANY,

    // Conditions below are only applicable for Player and Team entities
    BOTH, // Only applicable in Game Queries
    WHITE,
    BLACK,
    WINNER,
    LOSER,
}

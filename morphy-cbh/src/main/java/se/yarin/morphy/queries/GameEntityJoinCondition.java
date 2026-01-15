package se.yarin.morphy.queries;

import org.jetbrains.annotations.NotNull;
import se.yarin.chess.GameResult;
import se.yarin.morphy.Game;
import se.yarin.morphy.entities.EntityType;

import java.util.Set;

public enum GameEntityJoinCondition {
  // Default
  ANY,

  // Conditions below are only applicable for Player and Team entities
  BOTH, // Only applicable in Game Queries
  WHITE,
  BLACK,
  WINNER,
  LOSER;

  public boolean matches(
      int whiteId, int blackId, @NotNull GameResult result, @NotNull Set<Integer> matchingIds) {
    return switch (this) {
      case ANY -> matchingIds.contains(whiteId) || matchingIds.contains(blackId);
      case BOTH -> matchingIds.contains(whiteId) && matchingIds.contains(blackId);
      case WHITE -> matchingIds.contains(whiteId);
      case BLACK -> matchingIds.contains(blackId);
      case WINNER ->
          switch (result) {
            case WHITE_WINS, WHITE_WINS_ON_FORFEIT -> matchingIds.contains(whiteId);
            case BLACK_WINS, BLACK_WINS_ON_FORFEIT -> matchingIds.contains(blackId);
            default -> false;
          };
      case LOSER ->
          switch (result) {
            case WHITE_WINS, WHITE_WINS_ON_FORFEIT -> matchingIds.contains(blackId);
            case BLACK_WINS, BLACK_WINS_ON_FORFEIT -> matchingIds.contains(whiteId);
            default -> false;
          };
    };
  }

  public int[][] getJoinIds(@NotNull Game data, @NotNull EntityType entityType) {
    // TODO: This could probably be made more efficient by providing a lambda so this huge switch
    // statements
    // don't have to be evaluated for every game. Benchmark this!
    if ((entityType == EntityType.PLAYER || entityType == EntityType.TEAM)
        && (this == GameEntityJoinCondition.ANY || this == GameEntityJoinCondition.BOTH)) {
      int entityId1 = entityType == EntityType.PLAYER ? data.whitePlayerId() : data.whiteTeamId();
      int entityId2 = entityType == EntityType.PLAYER ? data.blackPlayerId() : data.blackTeamId();
      if (this == GameEntityJoinCondition.ANY) {
        return new int[][] {{entityId1}, {entityId2}};
      }
      return new int[][] {{entityId1, entityId2}};
    } else {
      int entityId =
          switch (entityType) {
            case PLAYER ->
                switch (this) {
                  case WHITE -> data.whitePlayerId();
                  case BLACK -> data.blackPlayerId();
                  case WINNER ->
                      switch (data.result()) {
                        case WHITE_WINS, WHITE_WINS_ON_FORFEIT -> data.whitePlayerId();
                        case BLACK_WINS, BLACK_WINS_ON_FORFEIT -> data.blackPlayerId();
                        default -> -1;
                      };
                  case LOSER ->
                      switch (data.result()) {
                        case WHITE_WINS, WHITE_WINS_ON_FORFEIT -> data.blackPlayerId();
                        case BLACK_WINS, BLACK_WINS_ON_FORFEIT -> data.whitePlayerId();
                        default -> -1;
                      };
                  default -> -1;
                };
            case TOURNAMENT -> data.tournamentId();
            case ANNOTATOR -> data.annotatorId();
            case SOURCE -> data.sourceId();
            case TEAM ->
                switch (this) {
                  case WHITE -> data.whiteTeamId();
                  case BLACK -> data.blackTeamId();
                  case WINNER ->
                      switch (data.result()) {
                        case WHITE_WINS, WHITE_WINS_ON_FORFEIT -> data.whiteTeamId();
                        case BLACK_WINS, BLACK_WINS_ON_FORFEIT -> data.blackTeamId();
                        default -> -1;
                      };
                  case LOSER ->
                      switch (data.result()) {
                        case WHITE_WINS, WHITE_WINS_ON_FORFEIT -> data.blackTeamId();
                        case BLACK_WINS, BLACK_WINS_ON_FORFEIT -> data.whiteTeamId();
                        default -> -1;
                      };
                  default -> -1;
                };
            case GAME_TAG -> data.gameTagId();
          };
      return new int[][] {{entityId}};
    }
  }
}

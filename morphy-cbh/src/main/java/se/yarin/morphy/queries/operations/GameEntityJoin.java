package se.yarin.morphy.queries.operations;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.Game;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.queries.GameEntityJoinCondition;

public class GameEntityJoin {

    public static int[][] getJoinIds(@NotNull Game data, @NotNull EntityType entityType, @NotNull GameEntityJoinCondition joinCondition) {
        // TODO: This could probably be made more efficient by providing a lambda so this huge switch statements
        // don't have to be evaluated for every game. Benchmark this!
        if ((entityType == EntityType.PLAYER || entityType == EntityType.TEAM) && (joinCondition == GameEntityJoinCondition.ANY || joinCondition == GameEntityJoinCondition.BOTH)) {
            int entityId1 = entityType == EntityType.PLAYER ? data.whitePlayerId() : data.whiteTeamId();
            int entityId2 = entityType == EntityType.PLAYER ? data.blackPlayerId() : data.blackTeamId();
            if (joinCondition == GameEntityJoinCondition.ANY) {
                return new int[][]{{entityId1}, {entityId2}};
            }
            return new int[][]{{entityId1, entityId2}};
        } else {
            int entityId = switch (entityType) {
                case PLAYER -> switch (joinCondition) {
                    case WHITE -> data.whitePlayerId();
                    case BLACK -> data.blackPlayerId();
                    case WINNER -> switch (data.result()) {
                        case WHITE_WINS, WHITE_WINS_ON_FORFEIT -> data.whitePlayerId();
                        case BLACK_WINS, BLACK_WINS_ON_FORFEIT -> data.blackPlayerId();
                        default -> -1;
                    };
                    case LOSER -> switch (data.result()) {
                        case WHITE_WINS, WHITE_WINS_ON_FORFEIT -> data.blackPlayerId();
                        case BLACK_WINS, BLACK_WINS_ON_FORFEIT -> data.whitePlayerId();
                        default -> -1;
                    };
                    default -> -1;
                };
                case TOURNAMENT -> data.tournamentId();
                case ANNOTATOR -> data.annotatorId();
                case SOURCE -> data.sourceId();
                case TEAM -> switch (joinCondition) {
                    case WHITE -> data.whiteTeamId();
                    case BLACK -> data.blackTeamId();
                    case WINNER -> switch (data.result()) {
                        case WHITE_WINS, WHITE_WINS_ON_FORFEIT -> data.whiteTeamId();
                        case BLACK_WINS, BLACK_WINS_ON_FORFEIT -> data.blackTeamId();
                        default -> -1;
                    };
                    case LOSER -> switch (data.result()) {
                        case WHITE_WINS, WHITE_WINS_ON_FORFEIT -> data.blackTeamId();
                        case BLACK_WINS, BLACK_WINS_ON_FORFEIT -> data.whiteTeamId();
                        default -> -1;
                    };
                    default -> -1;
                };
                case GAME_TAG -> data.gameTagId();
            };
            return new int[][]{{entityId}};
        }
    }
}
